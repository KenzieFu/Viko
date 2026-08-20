package com.example.viko.di

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.viko.VikoApplication
import com.example.viko.view.screens.channel.SelectChannelViewModel
import com.example.viko.view.screens.directcall.DirectCallViewModel
import com.example.viko.view.screens.home.HomeViewModel
import com.example.viko.view.screens.notifications.NotificationsViewModel
import com.example.viko.view.screens.team.AddMemberViewModel
import com.example.viko.view.screens.team.ManageMemberViewModel
import com.example.viko.view.screens.team.MemberDetailViewModel

/**
 * The single factory every screen uses to build its ViewModel. Keeps the
 * "where do dependencies come from" answer in one file while DI stays manual.
 *
 * ViewModels that need a route argument pull it from the `SavedStateHandle`,
 * which Navigation fills with the arguments of the destination they belong to.
 */
object AppViewModelProvider {

    val Factory = viewModelFactory {
        initializer {
            HomeViewModel(
                workerRepository = container().workerRepository,
                channelRepository = container().channelRepository,
                alertRepository = container().alertRepository
            )
        }
        initializer {
            NotificationsViewModel(alertRepository = container().alertRepository)
        }
        initializer {
            DirectCallViewModel(
                savedStateHandle = createSavedStateHandle(),
                workerRepository = container().workerRepository,
                messageRepository = container().messageRepository,
                badgeRegistry = container().badgeRegistry,
                audio = container().badgeAudio
            )
        }
        initializer {
            ManageMemberViewModel(
                workerRepository = container().workerRepository,
                channelRepository = container().channelRepository
            )
        }
        initializer {
            MemberDetailViewModel(
                savedStateHandle = createSavedStateHandle(),
                workerRepository = container().workerRepository,
                channelRepository = container().channelRepository,
                messageRepository = container().messageRepository
            )
        }
        initializer {
            AddMemberViewModel(
                savedStateHandle = createSavedStateHandle(),
                workerRepository = container().workerRepository,
                channelRepository = container().channelRepository,
                deviceScanner = container().deviceScanner
            )
        }
        initializer {
            SelectChannelViewModel(
                savedStateHandle = createSavedStateHandle(),
                workerRepository = container().workerRepository,
                channelRepository = container().channelRepository
            )
        }
    }
}

private fun CreationExtras.container(): AppContainer =
    (this[APPLICATION_KEY] as VikoApplication).container
