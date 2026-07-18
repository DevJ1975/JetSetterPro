package com.jetsetter.pro.ui

import androidx.lifecycle.ViewModel
import com.jetsetter.pro.core.ai.ActionRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * App-shell ViewModel: hands the singleton [ActionRouter] to the composable that owns the
 * NavController ([JetSetterApp]), which collects `navEvents` and turns each IRIS tool side effect
 * into real navigation / intent work. No state of its own — the router is the state holder.
 */
@HiltViewModel
class AppNavViewModel @Inject constructor(
    val actionRouter: ActionRouter,
) : ViewModel()
