package com.taisau.android.common.navigation3

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

class NavEntryRegistrar(
    val register: EntryProviderScope<NavKey>.(navigator: Navigator) -> Unit
)
