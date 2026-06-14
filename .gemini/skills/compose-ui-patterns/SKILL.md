# Compose UI Patterns for SMSentry

## Metadata
- name: compose-ui-patterns
- description: Jetpack Compose patterns, accessibility rules, and UI conventions used in SMSentry

## Accessibility Rules (MANDATORY)

### Every Icon MUST have a contentDescription
```kotlin
// ✅ CORRECT
Icon(Icons.Default.Delete, contentDescription = "Delete message")

// ❌ WRONG — will fail accessibility audit
Icon(Icons.Default.Delete, contentDescription = null)
```

### How to verify
```powershell
# Search for any remaining null contentDescriptions
grep -rn "contentDescription = null" app/src/main/java/com/smssentry/ui/
```
Zero results = passing.

### Pattern for parameterized icons
```kotlin
// In reusable components, derive from context:
@Composable
fun SettingsItem(title: String, icon: ImageVector, ...) {
    Icon(imageVector = icon, contentDescription = title)  // Use title as description
}
```

## String Resources (MANDATORY for UI text)
All user-visible strings MUST come from `strings.xml`:
```kotlin
// ✅ CORRECT
Text(stringResource(R.string.delete_conversation_title))

// ❌ WRONG — hardcoded string, breaks i18n
Text("Delete conversation?")
```

Current string resource file: `app/src/main/res/values/strings.xml` (160+ entries)

## Performance Patterns

### 1. Debounced ContentObservers
```kotlin
private var debounceJob: Job? = null

override fun onChange(selfChange: Boolean) {
    debounceJob?.cancel()
    debounceJob = viewModelScope.launch {
        delay(200L)  // 200ms for chat, 300ms for conversation list
        loadData()
    }
}
```

### 2. Animation Caps
Limit staggered entrance animations to prevent jank on long lists:
```kotlin
LaunchedEffect(Unit) {
    delay(minOf(index, 15) * 30L)  // Cap at 15 items, 30ms each
    itemVisible = true
}
```

### 3. SimpleDateFormat Caching
```kotlin
// ✅ CORRECT — cached in companion
companion object {
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
}

// ❌ WRONG — creates new instance per recomposition
val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
```

## SwipeToDismiss Pattern
```kotlin
val dismissState = rememberSwipeToDismissBoxState(
    confirmValueChange = { value ->
        when (value) {
            SwipeToDismissBoxValue.EndToStart -> {
                itemToDelete = item  // Show confirmation dialog
                false  // Don't actually dismiss yet
            }
            SwipeToDismissBoxValue.StartToEnd -> {
                viewModel.togglePin(item.id)
                false
            }
            else -> false
        }
    }
)

SwipeToDismissBox(
    state = dismissState,
    backgroundContent = { /* Red delete / Blue pin background */ },
    enableDismissFromStartToEnd = true,
    enableDismissFromEndToStart = true
) {
    // Actual item content
}
```

## Pagination in LazyColumn (reverseLayout)
```kotlin
// Since reverseLayout=true, oldest messages are at highest indices
val layoutInfo = listState.layoutInfo
val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
val totalItems = layoutInfo.totalItemsCount

LaunchedEffect(lastVisibleIndex) {
    if (canLoadMore && lastVisibleIndex >= totalItems - 4) {
        viewModel.loadMoreMessages()
    }
}
```

## Navigation Pattern
```kotlin
// Always pass minimal data via route, load details in ViewModel
NavHost(navController, startDestination = "conversations") {
    composable("conversations") { ConversationListScreen(...) }
    composable("chat/{threadId}/{address}",
        arguments = listOf(
            navArgument("threadId") { type = NavType.LongType },
            navArgument("address") { type = NavType.StringType }
        )
    ) { ChatScreen(...) }
}
```

## Common Gotchas
1. **ConversationListScreen is 886 lines** — deeply nested LazyColumn with SwipeToDismiss inside. Edit with extreme care.
2. **`else` must be last in `when`** — Compose compiler is strict about this.
3. **Remember scope** — `SimpleDateFormat` inside `items {}` must use `remember {}`.
4. **Surface onClick** — Use `Surface(onClick = {})` not `Modifier.clickable` for ripple + semantics.
