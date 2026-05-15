# Design System — Finanzas RD

> Tokens, componentes y guías visuales extraídas del prototipo de Claude Design. Este documento es la fuente de verdad visual para Claude Code al implementar Composables. Cada token tiene su equivalente Material 3 / Compose.

---

## 1. Tokens de color

### 1.1 Paleta primaria (azul institucional)

| Token CSS | Hex | Compose `Color(...)` | Uso |
|-----------|-----|----------------------|-----|
| `--c-primary` | `#2F6FED` | `Color(0xFF2F6FED)` | Botones primarios, links, indicador bottom-nav, dots activos |
| `--c-primary-600` | `#2960DA` | `Color(0xFF2960DA)` | Hover de primary |
| `--c-primary-50` | `#EAF1FE` | `Color(0xFFEAF1FE)` | Backgrounds suaves, hover de outline |
| `--c-primary-100` | `#D9E5FD` | `Color(0xFFD9E5FD)` | Bordes de outline button, dashed border |

### 1.2 Neutrales

| Token CSS | Hex | Compose | Uso |
|-----------|-----|---------|-----|
| `--c-ink` | `#0F172A` | `Color(0xFF0F172A)` | Texto principal, títulos |
| `--c-ink-2` | `#1E293B` | `Color(0xFF1E293B)` | Texto secundario fuerte |
| `--c-text` | `#334155` | `Color(0xFF334155)` | Texto de párrafo |
| `--c-muted` | `#64748B` | `Color(0xFF64748B)` | Labels, captions, texto secundario |
| `--c-muted-2` | `#94A3B8` | `Color(0xFF94A3B8)` | Chevrons, iconos terciarios |
| `--c-line` | `#E5E9F0` | `Color(0xFFE5E9F0)` | Bordes fuertes, dividers |
| `--c-line-2` | `#EEF1F5` | `Color(0xFFEEF1F5)` | Bordes suaves, hover, pill default |
| `--c-bg` | `#F4F6FA` | `Color(0xFFF4F6FA)` | Background de pantalla |
| `--c-card` | `#FFFFFF` | `Color.White` | Background de cards |
| `--c-dark` | `#0B1220` | `Color(0xFF0B1220)` | Splash, tarjetas oscuras |

### 1.3 Semánticos (income/expense/warning)

| Token CSS | Hex | Compose | Uso |
|-----------|-----|---------|-----|
| `--c-income` | `#16A34A` | `Color(0xFF16A34A)` | Texto/iconos de ingresos |
| `--c-income-50` | `#E7F7EC` | `Color(0xFFE7F7EC)` | Background suave de ingreso |
| `--c-expense` | `#DC2626` | `Color(0xFFDC2626)` | Texto/iconos de gastos, eliminación |
| `--c-expense-50` | `#FDECEC` | `Color(0xFFFDECEC)` | Background suave de gasto, danger outline |
| `--c-warning` | `#F59E0B` | `Color(0xFFF59E0B)` | Warning, duplicados, posibles errores |

### 1.4 Categorías

| Categoría | Color | Compose |
|-----------|-------|---------|
| `compras` | `#3B82F6` | `Color(0xFF3B82F6)` |
| `servicios` | `#EF4444` | `Color(0xFFEF4444)` |
| `transporte` | `#F59E0B` | `Color(0xFFF59E0B)` |
| `alimentacion` | `#10B981` | `Color(0xFF10B981)` |
| `otros` | `#94A3B8` | `Color(0xFF94A3B8)` |
| `salud` | `#EC4899` | `Color(0xFFEC4899)` |
| `pagos` | `#6366F1` | `Color(0xFF6366F1)` |
| `ingresos` | `#16A34A` | `Color(0xFF16A34A)` |

### 1.5 Colores de marca por banco

| Banco | Background | Texto |
|-------|-----------|-------|
| BanReservas | `#0F5DAB` | `#FFFFFF` |
| Popular | `#005DA4` | `#FFFFFF` |
| QIK | `#FFD200` | `#0B1220` |
| BHD | `#94A3B8` | `#FFFFFF` |
| Cibao | `#E30613` | `#FFFFFF` |

---

## 2. Material 3 ColorScheme

```kotlin
// theme/Color.kt
val FinanzasLightColorScheme = lightColorScheme(
    primary = Color(0xFF2F6FED),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF1FE),
    onPrimaryContainer = Color(0xFF0F172A),

    secondary = Color(0xFF16A34A),  // income
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7F7EC),

    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFDECEC),

    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF0F172A),

    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEEF1F5),
    onSurfaceVariant = Color(0xFF64748B),

    outline = Color(0xFFE5E9F0),
    outlineVariant = Color(0xFFEEF1F5),
)
```

---

## 3. Tipografía

### 3.1 Fuente

**Inter**, cargada vía Google Fonts en runtime con `androidx.compose.ui.text.googlefonts`.

```kotlin
val InterFont = GoogleFont("Inter")
val InterFamily = FontFamily(
    Font(InterFont, fontProvider, FontWeight.Normal),     // 400
    Font(InterFont, fontProvider, FontWeight.Medium),     // 500
    Font(InterFont, fontProvider, FontWeight.SemiBold),   // 600
    Font(InterFont, fontProvider, FontWeight.Bold),       // 700
    Font(InterFont, fontProvider, FontWeight.ExtraBold),  // 800
)
```

### 3.2 Escala tipográfica (extraída del prototipo)

| Nivel | Tamaño | Peso | Letter spacing | Uso |
|-------|--------|------|---------------|-----|
| Display Large | 36sp | 700 | -1.2sp | Hero del Login |
| Display Medium | 30sp | 700 | -0.8sp | Tasa de cambio destacada |
| Headline | 22sp | 700 | -0.6sp | Nombre de tarjeta, balance neto |
| Title Large | 20sp | 700 | -0.4sp | Header del reporte |
| Title Medium | 17sp | 600 | -0.3sp | App header, títulos de sección |
| Title Small | 16sp | 600 | -0.2sp | Subtítulos de cards |
| Body Large | 15sp | 500 | 0 | Texto de filas, botones |
| Body Medium | 14sp | 400 | 0 | Texto de párrafo, detalles |
| Body Small | 13sp | 500 | 0 | Pills, captions |
| Label Large | 12sp | 600 | 0.6sp uppercase | Section labels |
| Label Medium | 11sp | 500 | 0 | Stat card labels, micro-info |

### 3.3 Mapeo a `Typography` de Material 3

```kotlin
val FinanzasTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        letterSpacing = (-1.2).sp,
        lineHeight = 40.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = (-0.6).sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        letterSpacing = (-0.3).sp,
        lineHeight = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 0.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.6.sp,
        lineHeight = 16.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    ),
)
```

### 3.4 Números tabulares

Para montos, usar `tnum` (tabular numbers):

```kotlin
val TabularNumber = TextStyle(
    fontFeatureSettings = "tnum, ss01"
)

// Uso:
Text(
    text = "RD$ 42,850.00",
    style = MaterialTheme.typography.bodyLarge.merge(TabularNumber)
)
```

---

## 4. Espaciado y dimensiones

### 4.1 Sistema de spacing

```kotlin
object Spacing {
    val xxs = 4.dp
    val xs = 6.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 14.dp
    val xl = 16.dp     // padding lateral estándar de pantalla
    val xxl = 20.dp
    val xxxl = 24.dp
    val xxxxl = 32.dp
}
```

### 4.2 Radios de bordes

| Token | Valor | Compose | Uso |
|-------|-------|---------|-----|
| `--r-sm` | 8px | `RoundedCornerShape(8.dp)` | Inputs, dropdowns menores |
| `--r-md` | 12px | `RoundedCornerShape(12.dp)` | Botones, inputs, stat cards |
| `--r-lg` | 16px | `RoundedCornerShape(16.dp)` | Cards principales |
| `--r-xl` | 20px | `RoundedCornerShape(20.dp)` | Bottom sheets |
| `--r-pill` | 999px | `RoundedCornerShape(50)` | Pills, badges, switches |

```kotlin
object Radii {
    val sm = RoundedCornerShape(8.dp)
    val md = RoundedCornerShape(12.dp)
    val lg = RoundedCornerShape(16.dp)
    val xl = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(50)
}
```

### 4.3 Sombras (elevaciones Material 3)

| Uso | Compose |
|-----|---------|
| Cards normales | `Modifier.shadow(elevation = 1.dp, shape = Radii.lg)` |
| Dropdowns / popovers | `Modifier.shadow(elevation = 8.dp, shape = Radii.md)` |
| Tarjetas de crédito (oscuras) | `Modifier.shadow(elevation = 12.dp, shape = RoundedCornerShape(18.dp))` |
| Botón primario | `Modifier.shadow(elevation = 2.dp, shape = Radii.md, ambientColor = Color(0xFF2F6FED).copy(alpha = 0.25f))` |

---

## 5. Componentes core (especificaciones para Compose)

### 5.1 `Pill`

```kotlin
@Composable
fun Pill(
    text: String,
    active: Boolean = false,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null
) {
    Surface(
        onClick = onClick,
        shape = Radii.pill,
        color = if (active) MaterialTheme.colorScheme.primary
                else Color(0xFFEEF1F5),
        contentColor = if (active) Color.White
                       else Color(0xFF334155),
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            leadingIcon?.let { Icon(it, null, modifier = Modifier.size(14.dp)) }
            Text(text, style = MaterialTheme.typography.bodySmall)
            trailingIcon?.let { Icon(it, null, modifier = Modifier.size(12.dp)) }
        }
    }
}
```

### 5.2 `StatCard`

```kotlin
@Composable
fun StatCard(
    label: String,
    value: String,
    color: Color,
    background: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(background, Radii.md)
            .padding(14.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF64748B)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = (-0.2).sp,
                fontFeatureSettings = "tnum"
            ),
            color = color
        )
    }
}
```

### 5.3 `Switch` (custom — el de Material 3 tiene otra estética)

```kotlin
@Composable
fun FinanzasSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val bgColor by animateColorAsState(
        if (checked) Color(0xFF2F6FED) else Color(0xFFE5E9F0),
        animationSpec = tween(200)
    )
    val thumbOffset by animateDpAsState(
        if (checked) 20.dp else 2.dp,
        animationSpec = tween(200, easing = CubicBezierEasing(0.5f, 0f, 0.2f, 1f))
    )

    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 26.dp)
            .background(bgColor, RoundedCornerShape(13.dp))
            .clickable { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbOffset, top = 2.dp)
                .size(22.dp)
                .background(Color.White, CircleShape)
                .shadow(elevation = 1.dp, shape = CircleShape)
        )
    }
}
```

### 5.4 `CategoryIcon`

```kotlin
@Composable
fun CategoryIcon(
    categoria: String,
    size: Dp = 40.dp
) {
    val (color, icon) = CategoryRegistry.get(categoria)
    Box(
        modifier = Modifier
            .size(size)
            .background(color.copy(alpha = 0.13f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}
```

### 5.5 `MerchantLogo`

```kotlin
@Composable
fun MerchantLogo(
    logoKey: String,
    size: Dp = 40.dp
) {
    val (bg, fg, label) = MerchantRegistry.get(logoKey)
    Box(
        modifier = Modifier
            .size(size)
            .background(bg, RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.4f).sp
        )
    }
}
```

### 5.6 `BankLogo`

```kotlin
@Composable
fun BankLogo(
    bankCode: String,
    size: Dp = 36.dp
) {
    val bank = BankRegistry.get(bankCode)
    Box(
        modifier = Modifier
            .size(size)
            .background(bank.color, RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = bank.abbr,
            color = bank.textColor,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.32f).sp,
            letterSpacing = (-0.3).sp
        )
    }
}
```

### 5.7 `ActionRow` (settings list)

```kotlin
@Composable
fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    val color = if (danger) Color(0xFFDC2626) else Color(0xFF0F172A)
    val iconBg = if (danger) Color(0xFFFDECEC) else Color(0xFFEEF1F5)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconBg, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = color, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    it,
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        trailing?.invoke() ?: Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(18.dp)
        )
    }
}
```

### 5.8 `BottomNav`

```kotlin
@Composable
fun FinanzasBottomNav(
    active: String,
    onNavigate: (String) -> Unit
) {
    val items = listOf(
        NavItem("dashboard", "Inicio", Icons.Outlined.Home),
        NavItem("transactions", "Transacciones", Icons.Outlined.Receipt),
        NavItem("summary", "Resumen", Icons.Outlined.BarChart),
        NavItem("cards", "Tarjetas", Icons.Outlined.CreditCard),
        NavItem("more", "Más", Icons.Outlined.Menu)
    )

    Surface(
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = Color(0xFFEEF1F5),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            items.forEach { item ->
                val isActive = active == item.key
                val color = if (isActive) Color(0xFF2F6FED) else Color(0xFF64748B)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate(item.key) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(item.icon, null, tint = color, modifier = Modifier.size(22.dp))
                    Text(
                        item.label,
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
```

### 5.9 Card de transacción / list-row

```kotlin
@Composable
fun TransactionRow(
    merchant: String,
    category: String,
    amount: BigDecimal,
    moneda: Moneda,
    isIncome: Boolean,
    logoKey: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MerchantLogo(logoKey, size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                merchant,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF0F172A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                category,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B)
            )
        }
        Text(
            text = formatMoney(amount, moneda, withSign = isIncome),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            ),
            color = if (isIncome) Color(0xFF16A34A) else Color(0xFF0F172A)
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(16.dp)
        )
    }
}
```

---

## 6. Patrones de layout

### 6.1 Estructura de pantalla típica

```kotlin
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
    bottomNav: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            Surface(color = Color(0xFFF4F6FA)) {
                Column {
                    StatusBar()
                    AppHeader(title, onBack, trailing)
                }
            }
        },
        bottomBar = bottomNav,
        containerColor = Color(0xFFF4F6FA)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            content = content
        )
    }
}
```

### 6.2 Sección con cards

```
Padding horizontal pantalla: 16.dp
Padding interno de card: 16.dp (vertical y horizontal)
Gap entre cards de sección: 12.dp
Gap entre secciones: 20.dp
Section-label margin-top: 16-20.dp
```

### 6.3 Bottom sheets

```kotlin
@Composable
fun CategorySheet(
    open: Boolean,
    currentCategory: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!open) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(Color(0xFFE5E9F0), RoundedCornerShape(4.dp))
            )
        }
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                "Cambiar categoría",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp)
            )
            CategoryRegistry.all().forEach { cat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(cat.id) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CategoryIcon(cat.id, size = 36.dp)
                    Text(cat.nombre, style = MaterialTheme.typography.bodyLarge,
                         modifier = Modifier.weight(1f))
                    if (cat.id == currentCategory) {
                        Icon(Icons.Default.Check, null,
                             tint = MaterialTheme.colorScheme.primary,
                             modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
```

---

## 7. Animaciones (extraídas del CSS)

| Animación | Duración | Curve | Compose |
|-----------|----------|-------|---------|
| Fade-in entre pantallas | 240ms | ease | `fadeIn(tween(240))` |
| Slide-up bottom sheet | 280ms | ease | `slideInVertically(tween(280)) + fadeIn()` |
| Switch toggle | 200ms | cubic-bezier(0.5,0,0.2,1) | `tween(200, easing = CubicBezierEasing(0.5f, 0f, 0.2f, 1f))` |
| Pill state change | 140ms | linear | `tween(140)` |
| Pressable scale | 120ms | linear | `tween(120)` |
| Donut chart | 700ms | ease-out-cubic | `tween(700, easing = EaseOutCubic)` |
| Pulse (processing) | 900ms infinite | ease | `infiniteRepeatable(tween(900))` |
| Spinner | 700ms linear infinite | linear | `infiniteRepeatable(tween(700, easing = LinearEasing))` |

```kotlin
// Ejemplo de fade-in entre pantallas:
NavHost(...) {
    composable(
        route = "dashboard",
        enterTransition = { fadeIn(animationSpec = tween(240)) },
        exitTransition = { fadeOut(animationSpec = tween(240)) }
    ) { DashboardScreen() }
}
```

---

## 8. Iconografía

El prototipo usa SVGs propios estilo "outline stroke" de 24×24 con stroke-width 1.75. En Compose usaremos **Material Symbols Outlined**, manteniendo el mismo estilo.

```kotlin
// Mapeo de iconos del prototipo a Material Icons:
val IconMap = mapOf(
    "Home"        to Icons.Outlined.Home,
    "Receipt"     to Icons.Outlined.Receipt,
    "Chart"       to Icons.Outlined.BarChart,
    "Card"        to Icons.Outlined.CreditCard,
    "Menu"        to Icons.Outlined.Menu,
    "Bell"        to Icons.Outlined.Notifications,
    "Search"      to Icons.Outlined.Search,
    "Filter"      to Icons.Outlined.FilterList,
    "Plus"        to Icons.Outlined.Add,
    "Back"        to Icons.AutoMirrored.Outlined.ArrowBack,
    "Close"       to Icons.Outlined.Close,
    "More"        to Icons.Outlined.MoreHoriz,
    "ChevRight"   to Icons.AutoMirrored.Filled.KeyboardArrowRight,
    "ChevDown"    to Icons.Filled.KeyboardArrowDown,
    "ChevUp"      to Icons.Filled.KeyboardArrowUp,
    "Check"       to Icons.Outlined.Check,
    "CheckCircle" to Icons.Outlined.CheckCircle,
    "Upload"      to Icons.Outlined.CloudUpload,
    "Settings"    to Icons.Outlined.Settings,
    "User"        to Icons.Outlined.Person,
    "Building"    to Icons.Outlined.Business,
    "Tag"         to Icons.Outlined.LocalOffer,
    "Download"    to Icons.Outlined.Download,
    "Doc"         to Icons.Outlined.Description,
    "Refresh"     to Icons.Outlined.Refresh,
    "Edit"        to Icons.Outlined.Edit,
    "Trash"       to Icons.Outlined.DeleteOutline,
    "Globe"       to Icons.Outlined.Public,
    "Eye"         to Icons.Outlined.Visibility,
    "Clock"       to Icons.Outlined.Schedule,
    "ArrowUp"     to Icons.Outlined.ArrowUpward,
    "ArrowDown"   to Icons.Outlined.ArrowDownward,
    "TrendUp"     to Icons.AutoMirrored.Outlined.TrendingUp,
)
```

---

## 9. Formateo de números y fechas

```kotlin
// core/extensions/Format.kt

fun formatMoney(
    amount: BigDecimal,
    moneda: Moneda = Moneda.DOP,
    withSign: Boolean = false,
    decimals: Int = 2
): String {
    val prefix = when (moneda) {
        Moneda.DOP -> "RD$"
        Moneda.USD -> "US$"
    }
    val abs = amount.abs()
    val formatted = NumberFormat.getNumberInstance(Locale("es", "DO")).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }.format(abs)

    return when {
        withSign && amount > BigDecimal.ZERO -> "+ $prefix $formatted"
        amount < BigDecimal.ZERO -> "- $prefix $formatted"
        else -> "$prefix $formatted"
    }
}

fun formatDate(date: LocalDate): String {
    val months = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio",
                        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
    return "${date.dayOfMonth} ${months[date.monthValue - 1]}, ${date.year}"
}

fun formatDateRelative(date: LocalDate): String {
    val today = LocalDate.now(ZoneId.of("America/Santo_Domingo"))
    return when {
        date == today -> "Hoy"
        date == today.minusDays(1) -> "Ayer"
        else -> formatDate(date)
    }
}
```

---

## 10. Checklist de aplicación del design system

Cuando Claude Code implemente cualquier Composable, debe validar:

- [ ] Color de fondo del screen es `Color(0xFFF4F6FA)`
- [ ] Cards usan `Color.White` con `RoundedCornerShape(16.dp)` y borde sutil `Color(0xFFEEF1F5)`
- [ ] Padding lateral consistente de `16.dp`
- [ ] Tipografía Inter aplicada vía theme
- [ ] Números monetarios usan `fontFeatureSettings = "tnum"`
- [ ] Botones primarios con `#2F6FED` y sombra suave
- [ ] Ingresos en `#16A34A`, gastos en `#DC2626`
- [ ] Iconos outline de Material Symbols
- [ ] Bottom navigation con altura ~56.dp, items con icono 22.dp + label 11sp
- [ ] Press states con scale(0.98)
- [ ] Transiciones de pantalla con fade-in 240ms
