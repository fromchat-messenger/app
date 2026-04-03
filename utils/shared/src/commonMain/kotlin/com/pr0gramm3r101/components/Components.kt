@file:Suppress("unused", "MemberVisibilityCanBePrivate", "UnusedReceiverParameter", "NOTHING_TO_INLINE")

package com.pr0gramm3r101.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults.cardColors
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pr0gramm3r101.utils.invoke
import com.pr0gramm3r101.utils.left
import com.pr0gramm3r101.utils.link
import com.pr0gramm3r101.utils.plus
import com.pr0gramm3r101.utils.right
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tech.annexflow.constraintlayout.compose.ConstraintLayout
import tech.annexflow.constraintlayout.compose.ConstraintLayoutScope
import tech.annexflow.constraintlayout.compose.Dimension
import kotlin.random.Random

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    bodyModifier: Modifier = Modifier,
    headline: String,
    supportingText: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable ConstraintLayoutScope.() -> Unit)? = null,
    enabled: Boolean = true,
    divider: Boolean = false,
    dividerColor: Color = DividerDefaults.color,
    dividerThickness: Dp = DividerDefaults.Thickness,
    dividerAnimated: Boolean = false,
    onClick: (() -> Unit)? = null,
    bodyOnClick: (() -> Unit)? = null,
    leadingAndBodyShared: Boolean = false,
    bottomContent: (@Composable () -> Unit)? = null
) {
    Column {
        @Composable
        fun ProvideStyle(
            content: @Composable BoxScope?.() -> Unit
        ) {
            if (!enabled) {
                CompositionLocalProvider(
                    value = LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                ) {
                    Box(
                        modifier = Modifier.alpha(0.38f),
                        content = content
                    )
                }
            } else {
                content(null)
            }
        }

        ProvideStyle {
            ConstraintLayout(
                modifier = Modifier.let {
                    var mod = it.fillMaxWidth()
                    if (onClick != null) {
                        mod += Modifier.clickable(onClick = onClick)
                    }
                    mod + modifier
                }
            ) {
                val (leading, listItem, trailing, btm) = createRefs()

                @Composable
                fun leadingBody() {
                    if (leadingContent != null) {
                        Box(
                            modifier = Modifier
                                .constrainAs(leading) {
                                    top link parent.top
                                    bottom link
                                            if (bottomContent != null) btm.top
                                            else parent.bottom
                                    left link parent.left
                                    right link listItem.left
                                }
                                .padding(
                                    start = 16.dp,
                                    top = 8.dp,
                                    bottom = 8.dp
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            leadingContent()
                        }
                    }
                    ListItem(
                        headlineContent = { Text(headline) },
                        supportingContent = { if (supportingText != null) Text(supportingText) },
                        modifier = Modifier
                            .let {
                                var mod = it.constrainAs(listItem) {
                                    top link parent.top
                                    bottom link
                                            if (bottomContent != null) btm.top
                                            else parent.bottom

                                    left link
                                            if (leadingContent != null) leading.right
                                            else parent.left
                                    right link
                                            if (trailingContent != null) trailing.left
                                            else parent.right
                                    width = Dimension.fillToConstraints
                                }
                                if (bodyOnClick != null && !leadingAndBodyShared) {
                                    mod += Modifier.clickable(onClick = bodyOnClick)
                                }
                                mod + bodyModifier
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        )
                    )
                }

                if (leadingAndBodyShared) {
                    Row(
                        modifier = Modifier
                            .constrainAs(listItem) {
                                top link parent.top
                                bottom link
                                        if (bottomContent != null) btm.top
                                        else parent.bottom
                                left link parent.left
                                right link
                                        if (trailingContent != null) trailing.left
                                        else parent.right
                                width = Dimension.fillToConstraints
                            }
                                +
                                if (bodyOnClick != null)
                                    Modifier.clickable(onClick = bodyOnClick)
                                else
                                    Modifier,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        leadingBody()
                    }
                } else {
                    leadingBody()
                }

                if (trailingContent != null) {
                    ConstraintLayout(
                        modifier = Modifier
                            .constrainAs(trailing) {
                                top link parent.top
                                bottom link
                                        if (bottomContent != null) btm.top
                                        else parent.bottom
                                right link parent.right
                                left link listItem.right
                            }
                            .padding(
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 8.dp
                            ),
                        content = trailingContent
                    )
                }
                if (bottomContent != null) {
                    Box(
                        modifier = Modifier
                            .constrainAs(btm) {
                                bottom link parent.bottom
                                left link parent.left
                                right link parent.right
                                width = Dimension.fillToConstraints
                            }
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    ) {
                        bottomContent()
                    }
                }
            }
        }
        if (dividerAnimated) {
            AnimatedVisibility(
                visible = divider,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                HorizontalDivider(
                    color = dividerColor,
                    thickness = dividerThickness
                )
            }
        } else if (divider) {
            HorizontalDivider(
                color = dividerColor,
                thickness = dividerThickness
            )
        }
    }
}

@Composable
inline fun SwitchListItem(
    modifier: Modifier = Modifier,
    headline: String,
    supportingText: String? = null,
    noinline leadingContent: (@Composable () -> Unit)? = null,
    checked: Boolean,
    noinline onCheckedChange: (Boolean) -> Unit,
    crossinline listItemOnClick: () -> Unit = { onCheckedChange(!checked) },
    divider: Boolean = false,
    dividerColor: Color = DividerDefaults.color,
    dividerThickness: Dp = DividerDefaults.Thickness,
    dividerAnimated: Boolean = false,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    ListItem(
        modifier = if (enabled) Modifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication(),
            onClick = { listItemOnClick() }
        ) + modifier else modifier,
        headline = headline,
        supportingText = supportingText,
        leadingContent = leadingContent,
        trailingContent = {
            val (sw) = createRefs()
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                interactionSource = interactionSource,
                enabled = enabled,
                modifier = Modifier.constrainAs(sw) {
                    top link parent.top
                    bottom link parent.bottom
                    right link parent.right
                }
            )
        },
        divider = divider,
        dividerColor = dividerColor,
        dividerThickness = dividerThickness,
        dividerAnimated = dividerAnimated,
        enabled = enabled
    )
}

@Composable
inline fun SeparatedSwitchListItem(
    modifier: Modifier = Modifier,
    bodyModifier: Modifier = Modifier,
    headline: String,
    supportingText: String? = null,
    noinline leadingContent: (@Composable () -> Unit)? = null,
    checked: Boolean,
    noinline onCheckedChange: (Boolean) -> Unit,
    noinline bodyOnClick: () -> Unit,
    divider: Boolean = false,
    dividerColor: Color = DividerDefaults.color,
    dividerThickness: Dp = DividerDefaults.Thickness,
    dividerAnimated: Boolean = false,
    enabled: Boolean = true
) {
    ListItem(
        modifier = modifier,
        bodyModifier = bodyModifier,
        bodyOnClick = bodyOnClick,
        headline = headline,
        supportingText = supportingText,
        leadingContent = leadingContent,
        trailingContent = {
            val (div, switch) = createRefs()
            VerticalDivider(
                modifier = Modifier
                    .padding()
                    .padding(end = 16.dp)
                    .constrainAs(div) {
                        top link parent.top
                        bottom link parent.bottom
                        left link parent.left
                        right link switch.left
                        height = Dimension.fillToConstraints
                    }
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.constrainAs(switch) {
                    top link parent.top
                    bottom link parent.bottom
                    right link parent.right
                    left link div.right
                },
                enabled = enabled
            )
        },
        divider = divider,
        dividerColor = dividerColor,
        dividerThickness = dividerThickness,
        dividerAnimated = dividerAnimated,
        leadingAndBodyShared = true,
        enabled = enabled
    )
}

@Composable
fun SwitchCard(
    modifier: Modifier = Modifier,
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    cardOnClick: () -> Unit = { onCheckedChange(!checked) }
) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            + modifier,
        colors = cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(25.dp)
    ) {
        Box(
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = LocalIndication(),
                onClick = cardOnClick
            )
        ) {
            ConstraintLayout(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                val (txt, switch) = createRefs()
                Box(
                    modifier = Modifier.constrainAs(txt) {
                        top link parent.top
                        bottom link parent.bottom
                        left link parent.left
                        right link switch.left
                        width = Dimension.fillToConstraints
                        height = Dimension.fillToConstraints
                    },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = text,
                        fontSize = 20.sp
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.constrainAs(switch) {
                        top link parent.top
                        bottom link parent.bottom
                        right link parent.right
                    },
                    interactionSource = interactionSource
                )
            }
        }
    }
}


// TODO fix
/*@Composable
inline fun SecureTextField(
    value: String,
    noinline onValueChange: (String) -> Unit,
    passwordHidden: Boolean,
    noinline visibilityOnClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = LocalTextStyle(),
    noinline label: @Composable (() -> Unit)? = null,
    noinline leadingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors()
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        visualTransformation =
        if (!passwordHidden)
            VisualTransformation.None
        else
            PasswordVisualTransformation(),
        label = label,
        trailingIcon = {
            IconButton(
                onClick = visibilityOnClick
            ) {
                val visibilityIcon =
                    if (passwordHidden) Icons.Filled.Visibility else
                        Icons.Filled.VisibilityOff
                // Provide localized description for accessibility services
                val description =
                    if (passwordHidden)
                        stringResource(R.string.show_pw)
                    else
                        stringResource(R.string.hide_pw)
                Icon(imageVector = visibilityIcon, contentDescription = description)
            }
        },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false
        ),
        maxLines = 1,
        singleLine = true,
        enabled = enabled,
        textStyle = textStyle,
        leadingIcon = leadingIcon,
        isError = isError,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        shape = shape,
        colors = colors
    )
}*/

@Composable
inline fun ToggleIconButton(
    checked: Boolean,
    crossinline onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = IconButtonDefaults.filledShape,
    checkedColors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    uncheckedColors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    noinline content: @Composable () -> Unit
) {
    if (checked) {
        FilledIconButton(
            onClick = { onCheckedChange(false) },
            modifier = Modifier.size(60.dp, 60.dp) + modifier,
            enabled = enabled,
            shape = shape,
            colors = checkedColors,
            interactionSource = interactionSource,
            content = content
        )
    } else {
        FilledTonalIconButton(
            onClick = { onCheckedChange(true) },
            modifier = Modifier.size(60.dp, 60.dp) + modifier,
            enabled = enabled,
            shape = shape,
            colors = uncheckedColors,
            interactionSource = interactionSource,
            content = content
        )
    }
}

@Composable
inline fun ElevatedButton(
    noinline onClick: () -> Unit,
    crossinline icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.elevatedShape,
    colors: ButtonColors = ButtonDefaults.elevatedButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.elevatedButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ButtonWithIconContentPadding,
    interactionSource: MutableInteractionSource? = null,
    crossinline content: @Composable RowScope.() -> Unit
) {
    ElevatedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier.size(18.dp)
        ) {
            icon()
        }
        Spacer(Modifier.width(8.dp))
        content()
    }
}

@Composable
inline fun Button(
    noinline onClick: () -> Unit,
    crossinline icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ButtonWithIconContentPadding,
    interactionSource: MutableInteractionSource? = null,
    crossinline content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier.size(18.dp)
        ) {
            icon()
        }
        Spacer(Modifier.width(8.dp))
        content()
    }
}

@Composable
inline fun FilledTonalButton(
    noinline onClick: () -> Unit,
    crossinline icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ButtonWithIconContentPadding,
    interactionSource: MutableInteractionSource? = null,
    crossinline content: @Composable RowScope.() -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier.size(18.dp)
        ) {
            icon()
        }
        Spacer(Modifier.width(8.dp))
        content()
    }
}

@Composable
inline fun TextButton(
    noinline onClick: () -> Unit,
    crossinline icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonWithIconContentPadding,
    interactionSource: MutableInteractionSource? = null,
    crossinline content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier.size(18.dp)
        ) {
            icon()
        }
        Spacer(Modifier.width(8.dp))
        content()
    }
}

class RadioButtonControllerScope<T> @PublishedApi internal constructor() {
    private val radioButtons = mutableMapOf<T, MutableState<Boolean>>()

    val checkedItemAsState: MutableState<T?> = mutableStateOf(null)

    private fun processCheckedChange(id: T) {
        val toModify = mutableListOf<T>()

        radioButtons.forEach { (i) ->
            if (i != id) {
                toModify.add(i)
            }
        }

        toModify.forEach { i ->
            radioButtons[i]!!.value = false
        }
    }

    fun addRadioButton(id: T, checked: MutableState<Boolean>, coroutineScope: CoroutineScope) {
        radioButtons[id] = checked
        coroutineScope.launch {
            snapshotFlow { checked.value }.collect {
                if (it) {
                    processCheckedChange(id)
                    checkedItemAsState.value = checkedItem
                }
            }
        }
    }

    fun addRadioButtons(
        vararg radioButtons: Pair<T, MutableState<Boolean>>,
        coroutineScope: CoroutineScope
    ) =
        radioButtons.forEach { (id, checked) ->
            addRadioButton(id, checked, coroutineScope)
        }

    fun removeRadioButton(id: T) = radioButtons.remove(id)

    fun isChecked(id: T) = radioButtons[id]!!.value
    val checkedItem: T?
        get() = radioButtons.firstNotNullOfOrNull { if (it.value.value) it.key else null }

    fun clearCheckedItems() = radioButtons.values.forEach { it.value = false }

    interface Id<T> {
        val id: T
    }

    class RandomIntId internal constructor(): Id<Int> {
        override val id = Random.nextInt()
    }

    class OrderedIntId internal constructor(override val id: Int): Id<Int>

    abstract class Ids<IdT: Id<T>, T> {
        abstract operator fun component1(): IdT
        abstract operator fun component2(): IdT
        abstract operator fun component3(): IdT
        abstract operator fun component4(): IdT
        abstract operator fun component5(): IdT
        abstract operator fun component6(): IdT
        abstract operator fun component7(): IdT
        abstract operator fun component8(): IdT
        abstract operator fun component9(): IdT
        abstract operator fun component10(): IdT
        abstract operator fun component11(): IdT
        abstract operator fun component12(): IdT
        abstract operator fun component13(): IdT
        abstract operator fun component14(): IdT
        abstract operator fun component15(): IdT
        abstract operator fun component16(): IdT
    }

    class OrderedIntIds internal constructor(): Ids<OrderedIntId, Int>() {
        override fun component1() = OrderedIntId(0)
        override fun component2() = OrderedIntId(1)
        override fun component3() = OrderedIntId(2)
        override fun component4() = OrderedIntId(3)
        override fun component5() = OrderedIntId(4)
        override fun component6() = OrderedIntId(5)
        override fun component7() = OrderedIntId(6)
        override fun component8() = OrderedIntId(7)
        override fun component9() = OrderedIntId(8)
        override fun component10() = OrderedIntId(9)
        override fun component11() = OrderedIntId(10)
        override fun component12() = OrderedIntId(11)
        override fun component13() = OrderedIntId(12)
        override fun component14() = OrderedIntId(13)
        override fun component15() = OrderedIntId(14)
        override fun component16() = OrderedIntId(15)
    }

    @Suppress("SameReturnValue")
    class Ints internal constructor() {
        operator fun component1() = 0
        operator fun component2() = 1
        operator fun component3() = 2
        operator fun component4() = 3
        operator fun component5() = 4
        operator fun component6() = 5
        operator fun component7() = 6
        operator fun component8() = 7
        operator fun component9() = 8
        operator fun component10() = 9
        operator fun component11() = 10
        operator fun component12() = 11
        operator fun component13() = 12
        operator fun component14() = 13
        operator fun component15() = 14
        operator fun component16() = 15
    }

    fun createRandomId() = RandomIntId()
    fun createRandomIds() = object: Ids<Id<Int>, Int>() {
        override operator fun component1() = RandomIntId()
        override operator fun component2() = RandomIntId()
        override operator fun component3() = RandomIntId()
        override operator fun component4() = RandomIntId()
        override operator fun component5() = RandomIntId()
        override operator fun component6() = RandomIntId()
        override operator fun component7() = RandomIntId()
        override operator fun component8() = RandomIntId()
        override operator fun component9() = RandomIntId()
        override operator fun component10() = RandomIntId()
        override operator fun component11() = RandomIntId()
        override operator fun component12() = RandomIntId()
        override operator fun component13() = RandomIntId()
        override operator fun component14() = RandomIntId()
        override operator fun component15() = RandomIntId()
        override operator fun component16() = RandomIntId()
    }

    fun createOrderedId(index: Int) = OrderedIntId(index)
    fun createOrderedIds() = OrderedIntIds()

    fun createIntIds() = Ints()
}

@Composable
inline fun <T> rememberRadioButtonController() = rememberSaveable { RadioButtonControllerScope<T>() }

@Composable
inline fun <T> RadioButtonController(
    content: @Composable RadioButtonControllerScope<T>.() -> Unit
) {
    content(RadioButtonControllerScope())
}

@Composable
inline fun CheckboxWithText(
    checked: Boolean,
    crossinline onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(contentPadding)
            +
            if (enabled)
                Modifier.toggleable(
                    value = checked,
                    onValueChange = { onCheckedChange(!checked) },
                    role = Role.Checkbox
                )
            else Modifier
            + modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = colors,
        )
        Spacer(modifier = Modifier.width(8.dp))
        content()
    }
}

@Composable
inline fun SwitchWithText(
    checked: Boolean,
    crossinline onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(contentPadding)
            +
            if (enabled)
                Modifier.toggleable(
                    value = checked,
                    onValueChange = { onCheckedChange(!checked) },
                    role = Role.Switch
                )
            else Modifier
            + modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        Spacer(modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = colors,
        )
    }
}


@Composable
inline fun RadioButtonWithText(
    modifier: Modifier = Modifier,
    selected: Boolean,
    crossinline onSelectedChange: () -> Unit,
    enabled: Boolean = true,
    crossinline content: @Composable () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .selectable(
                selected = selected,
                onClick = {
                    if (enabled) onSelectedChange()
                },
                role = Role.RadioButton
            )
            .padding(horizontal = 16.dp)
            + modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Spacer(modifier = Modifier.width(16.dp))
        ProvideTextStyle(value = MaterialTheme.typography.bodyLarge) {
            content()
        }
    }
}

@Composable
inline fun SimpleAlertDialog(
    open: Boolean,
    crossinline onDismissRequest: () -> Unit,
    crossinline onPositiveButtonClick: (() -> Unit) = {},
    crossinline onNegativeButtonClick: (() -> Unit) = {},
    positiveButtonText: String? = null,
    negativeButtonText: String? = null,
    title: String,
    body: String? = null,
    dsaString: String? = null,
    crossinline onDsa: (() -> Unit) = {},
    noinline icon: @Composable (() -> Unit) = {},
) {
    if (open) {
        var dsaChecked by remember { mutableStateOf(false) }

        AlertDialog(
            icon = icon,
            title = {
                Text(text = title)
            },
            text =
            if (body != null || dsaString != null) {
                {
                    if (body != null) {
                        Text(text = body)
                    }
                    if (dsaString != null) {
                        CheckboxWithText(
                            checked = dsaChecked,
                            onCheckedChange = { dsaChecked = it },
                            contentPadding = PaddingValues(top = 10.dp)
                        ) {
                            Text(text = dsaString)
                        }
                    }
                }
            } else null,
            onDismissRequest = { onDismissRequest() },
            confirmButton = {
                if (!positiveButtonText.isNullOrBlank()) {
                    TextButton(
                        onClick = {
                            onPositiveButtonClick()
                            if (dsaChecked) onDsa()
                            onDismissRequest()
                        }
                    ) {
                        Text(positiveButtonText)
                    }
                }
            },
            dismissButton = if (!negativeButtonText.isNullOrBlank()) {
                {
                    TextButton(
                        onClick = {
                            onNegativeButtonClick()
                            if (dsaChecked) onDsa()
                            onDismissRequest()
                        }
                    ) {
                        Text(negativeButtonText)
                    }
                }
            } else null
        )
    }
}

@Composable
inline fun RadioGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.selectableGroup() + modifier) {
        content()
    }
}

@Composable
inline fun SwipeToDismissBackground(
    dismissState: SwipeToDismissBoxState,
    startToEndColor: Color = Color(0xFFFF1744),
    startToEndIcon: @Composable () -> Unit = {
        Icon(
            Icons.Default.Delete,
            contentDescription = "Delete"
        )
    },
    endToStartColor: Color = Color(0xFF1DE9B6),
    endToStartIcon: @Composable () -> Unit = {
        Icon(
            Icons.Filled.Archive,
            contentDescription = "Archive"
        )
    }
) {
    val color = when (dismissState.dismissDirection) {
        SwipeToDismissBoxValue.StartToEnd -> startToEndColor
        SwipeToDismissBoxValue.EndToStart -> endToStartColor
        SwipeToDismissBoxValue.Settled -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(12.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        startToEndIcon()
        Spacer(modifier = Modifier)
        endToStartIcon()
    }
}

object CategoryDefaults {
    val margin = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp)
    val dividerColor @Composable get() = MaterialTheme.colorScheme.surface
    val dividerThickness: Dp = 2.dp
}

@Composable
inline fun ColumnScope.Category(
    modifier: Modifier = Modifier,
    title: String? = null,
    margin: PaddingValues = CategoryDefaults.margin,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    noinline content: @Composable ColumnScope.() -> Unit
) {
    if (title != null) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 10.dp, start = 32.dp, end = 32.dp)
        )
    }
    Card(
        modifier = modifier
            .padding(margin)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = cardColors(containerColor = containerColor),
        content = content
    )
}