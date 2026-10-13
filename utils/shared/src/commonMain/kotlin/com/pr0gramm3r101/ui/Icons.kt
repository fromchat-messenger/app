@file:Suppress("UnusedReceiverParameter", "unused")

package com.pr0gramm3r101.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.rounded.Language
import androidx.compose.runtime.Composable
import com.pr0gramm3r101.shared.Res
import com.pr0gramm3r101.shared.license
import com.pr0gramm3r101.shared.siren_filled
import com.pr0gramm3r101.shared.siren_outlined
import org.jetbrains.compose.resources.vectorResource

inline val Icons.Outlined.Internet get() = Icons.Outlined.Language
inline val Icons.Filled.Internet get() = Icons.Filled.Language

inline val Icons.Outlined.Website get() = Icons.Outlined.Language
inline val Icons.Filled.Website get() = Icons.Filled.Language
inline val Icons.Rounded.Website get() = Icons.Rounded.Language

inline val Icons.Outlined.License
    @Composable get() = vectorResource(Res.drawable.license)
inline val Icons.Filled.License
    @Composable get() = Icons.Outlined.License

inline val Icons.Outlined.Siren
    @Composable get() = vectorResource(Res.drawable.siren_outlined)
inline val Icons.Filled.Siren
    @Composable get() = vectorResource(Res.drawable.siren_filled)