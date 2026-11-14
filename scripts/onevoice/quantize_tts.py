"""Weight quantization for the parts omitted by the upstream voice exports."""

from pathlib import Path
from tempfile import TemporaryDirectory

import numpy as np
import onnx
from onnx import helper, numpy_helper
from onnxruntime.quantization import QuantType, quantize_dynamic


def quantize_supertonic(source, destination):
    model = onnx.load(source)
    graph = model.graph
    weights = {value.name: value for value in graph.initializer}
    nodes = []
    replaced_weights = set()
    for node in graph.node:
        weight = weights.get(node.input[1]) if node.op_type == "Conv" else None
        attributes = {a.name: helper.get_attribute_value(a) for a in node.attribute}
        if (weight is None or len(weight.dims) != 3 or weight.dims[2] != 1
                or attributes.get("group", 1) != 1
                or attributes.get("strides", [1]) != [1]
                or attributes.get("pads", [0, 0]) != [0, 0]
                or attributes.get("auto_pad", b"NOTSET") not in (b"NOTSET", b"VALID")):
            nodes.append(node)
            continue

        # Dynamic INT8 MatMul runs on Android's CPU provider. A 1x1 Conv1d
        # applies the same matrix to each time step independently.
        prefix = node.output[0] + "_pointwise"
        matrix = numpy_helper.to_array(weight)[:, :, 0].T.copy()
        graph.initializer.append(numpy_helper.from_array(matrix, prefix + "_weight"))
        replaced_weights.add(weight.name)
        nodes.append(helper.make_node("Transpose", [node.input[0]], [prefix + "_input"], perm=[0, 2, 1]))
        nodes.append(helper.make_node("MatMul", [prefix + "_input", prefix + "_weight"], [prefix + "_dot"]))
        result = prefix + "_dot"
        if len(node.input) == 3:
            nodes.append(helper.make_node("Add", [result, node.input[2]], [prefix + "_biased"]))
            result = prefix + "_biased"
        nodes.append(helper.make_node("Transpose", [result], list(node.output), perm=[0, 2, 1]))

    retained = {name for node in nodes for name in node.input} | {value.name for value in graph.output}
    for weight in list(graph.initializer):
        if weight.name in replaced_weights and weight.name not in retained:
            graph.initializer.remove(weight)
    del graph.node[:]
    graph.node.extend(nodes)
    with TemporaryDirectory(dir=destination.parent) as temp:
        rewritten = Path(temp) / "pointwise.onnx"
        compressed = Path(temp) / "int8.onnx"
        onnx.save(onnx.shape_inference.infer_shapes(model), rewritten)
        quantize_dynamic(
            rewritten, compressed, op_types_to_quantize=["MatMul"],
            weight_type=QuantType.QInt8, per_channel=True,
            extra_options={"MatMulConstBOnly": True},
        )
        onnx.checker.check_model(compressed)
        if compressed.stat().st_size >= source.stat().st_size:
            raise ValueError(f"Quantization did not shrink {source.name}")
        compressed.replace(destination)


def write_embeddings(destination, text, audio, anchor):
    rows = np.concatenate((text, audio.reshape(-1, text.shape[-1]))).astype(np.float32)
    scales = np.max(np.abs(rows), axis=1) / 127
    scales[scales == 0] = 1
    weights = np.rint(rows / scales[:, None]).clip(-127, 127).astype(np.int8)
    with destination.open("wb") as output:
        weights.tofile(output)
        scales.astype("<f4").tofile(output)
        anchor.astype("<f4").tofile(output)
