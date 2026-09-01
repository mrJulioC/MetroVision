package com.solumetals.metrovision

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.ar.core.Frame
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import io.github.sceneview.ar.ARSceneView
import java.util.Locale

private val Ink = Color(0xFF071815)
private val Mint = Color(0xFF00D8B4)
private val Fog = Color(0xFFF5F7F6)
private val Slate = Color(0xFF52615E)

enum class Page { HOME, CAMERA, GPS }
enum class MeasureMode(val title: String) {
    DISTANCE("Distancia"), OBJECT("Objeto automático"), RECTANGLE("Rectángulo"), AREA("Área libre"), CALIBRATE("Calibrar")
}
enum class MeasureUnit(val label: String, val short: String) {
    METERS("Metros", "m"), CENTIMETERS("Centímetros", "cm"), FEET("Pies", "ft"), INCHES("Pulgadas", "in")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MetroTheme { MetroApp() } }
    }
}

@Composable private fun MetroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink,
            secondary = Mint,
            background = Fog,
            surface = Color.White,
            onSurface = Ink
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}

@Composable private fun MetroApp() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("metrovision", android.content.Context.MODE_PRIVATE) }
    var page by remember { mutableStateOf(Page.HOME) }
    var mode by remember { mutableStateOf(MeasureMode.DISTANCE) }
    var unit by remember { mutableStateOf(MeasureUnit.METERS) }
    val savedCorrection = preferences.getFloat("camera_correction", 1f).toDouble()
    var correction by remember { mutableDoubleStateOf(if (savedCorrection in .5..2.0) savedCorrection else 1.0) }
    when (page) {
        Page.HOME -> Home(unit, { unit = it }, correction, {
            correction = 1.0
            preferences.edit().remove("camera_correction").apply()
        }, onCamera = { mode = it; page = Page.CAMERA }, onGps = { page = Page.GPS })
        Page.CAMERA -> CameraMeasure(mode, unit, { unit = it }, correction, {
            correction = it
            preferences.edit().putFloat("camera_correction", it.toFloat()).apply()
        }, onBack = { page = Page.HOME })
        Page.GPS -> GpsMeasure(unit, { unit = it }, onBack = { page = Page.HOME })
    }
}

@Composable private fun Home(unit: MeasureUnit, unitChange: (MeasureUnit)->Unit, correction: Double, resetCalibration: () -> Unit, onCamera: (MeasureMode) -> Unit, onGps: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Fog).padding(22.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(com.solumetals.metrovision.R.drawable.metrovision_icon_source), "MetroVision", Modifier.size(62.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text("MetroVision", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("Medición inteligente", color = Slate, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(18.dp)); UnitSelector(unit, unitChange)
        if (correction != 1.0) {
            Spacer(Modifier.height(10.dp))
            Text("Cámara calibrada · factor %.3f".format(correction), fontSize = 12.sp, color = Slate)
        }
        Spacer(Modifier.height(30.dp))
        FeatureCard(Icons.Outlined.Straighten, "Distancia", "Marca los puntos A y B", Mint) { onCamera(MeasureMode.DISTANCE) }
        FeatureCard(Icons.Outlined.CenterFocusStrong, "Objeto automático", "Detecta el objeto y estima su ancho", Color(0xFF38BDF8)) { onCamera(MeasureMode.OBJECT) }
        FeatureCard(Icons.Outlined.CropSquare, "Pared o rectángulo", "Ancho, alto, área y perímetro", Color(0xFF7BA6FF)) { onCamera(MeasureMode.RECTANGLE) }
        FeatureCard(Icons.Outlined.Pentagon, "Área libre", "Marca todos los vértices", Color(0xFFFFB45B)) { onCamera(MeasureMode.AREA) }
        FeatureCard(Icons.Outlined.Tune, "Calibración", "Corrige usando una medida conocida", Color(0xFFA98BFF)) { onCamera(MeasureMode.CALIBRATE) }
        if (correction != 1.0) TextButton(onClick = resetCalibration, modifier = Modifier.align(Alignment.End)) { Text("Restablecer calibración") }
        FeatureCard(Icons.Outlined.Route, "Recorrido GPS", "Camina desde A hasta B", Color(0xFFFF718B), onGps)
        Spacer(Modifier.height(18.dp))
        Text("Las medidas de cámara son estimaciones. Para cortes o instalaciones críticas, confirma con una cinta o metro láser.", fontSize = 12.sp, color = Color(0xFF71807C))
    }
}

@Composable private fun FeatureCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String, color: Color, action: () -> Unit) {
    Card(onClick = action, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).background(color.copy(alpha=.17f), RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color) }
            Spacer(Modifier.width(15.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold, color = Ink); Text(text, fontSize = 13.sp, color = Color(0xFF71807C)) }
            Icon(Icons.Outlined.ChevronRight, null, tint = Color(0xFFA5B0AD))
        }
    }
}

@Composable private fun CameraMeasure(mode: MeasureMode, unit: MeasureUnit, unitChange: (MeasureUnit)->Unit, correction: Double, correctionChange: (Double)->Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    LaunchedEffect(Unit) { if (!permission) launcher.launch(Manifest.permission.CAMERA) }
    var frame by remember { mutableStateOf<Frame?>(null) }
    var points by remember { mutableStateOf(listOf<Point3>()) }
    var knownText by remember { mutableStateOf(if (unit == MeasureUnit.CENTIMETERS) "50" else "0.50") }
    var message by remember { mutableStateOf("Mueve lentamente el teléfono para detectar la superficie") }
    var proximityMeters by remember { mutableStateOf<Double?>(null) }
    var detectedWidthPx by remember { mutableStateOf<Double?>(null) }
    var detectorFocalPx by remember { mutableStateOf<Double?>(null) }
    var detectedLabel by remember { mutableStateOf("Buscando objeto") }
    var detectorBusy by remember { mutableStateOf(false) }
    var detectorFrame by remember { mutableIntStateOf(0) }
    val objectDetector = remember {
        ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableClassification()
                .enableMultipleObjects()
                .build()
        )
    }
    DisposableEffect(objectDetector) { onDispose { objectDetector.close() } }

    Box(Modifier.fillMaxSize().background(Ink)) {
        if (permission) ARSceneView(Modifier.fillMaxSize(), planeRenderer = true, onSessionUpdated = { _, f ->
            frame = f
            val display = context.resources.displayMetrics
            val hit = f.hitTest(display.widthPixels / 2f, display.heightPixels / 2f)
                .firstOrNull { it.trackable.trackingState == com.google.ar.core.TrackingState.TRACKING }
            proximityMeters = hit?.let { distance(f.camera.pose.point(), it.hitPose.point()) }
            if (mode == MeasureMode.OBJECT && !detectorBusy && detectorFrame++ % 12 == 0) {
                try {
                    val cameraImage = f.acquireCameraImage()
                    val focal = f.camera.imageIntrinsics.focalLength[1].toDouble()
                    detectorBusy = true
                    objectDetector.process(InputImage.fromMediaImage(cameraImage, 90))
                        .addOnSuccessListener { objects ->
                            val found = objects.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                            detectedWidthPx = found?.boundingBox?.width()?.toDouble()
                            detectorFocalPx = focal
                            detectedLabel = found?.labels?.maxByOrNull { it.confidence }?.text ?: if (found != null) "Objeto detectado" else "Buscando objeto"
                        }
                        .addOnCompleteListener {
                            cameraImage.close()
                            detectorBusy = false
                        }
                } catch (_: com.google.ar.core.exceptions.NotYetAvailableException) { }
            }
        })
        else Text("Se necesita permiso de cámara", color = Color.White, modifier = Modifier.align(Alignment.Center))
        Crosshair()
        Row(Modifier.statusBarsPadding().padding(12.dp).align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, colors = IconButtonDefaults.iconButtonColors(containerColor = Ink.copy(alpha=.72f))) { Icon(Icons.Outlined.ArrowBack, null, tint = Color.White) }
            Spacer(Modifier.width(8.dp)); Surface(color = Ink.copy(alpha=.72f), shape = RoundedCornerShape(18.dp)) { Text(mode.title, color = Color.White, modifier = Modifier.padding(horizontal=15.dp, vertical=10.dp), fontWeight = FontWeight.SemiBold) }
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(14.dp)) {
            ResultPanel(mode, points, correction, unit, knownText, { knownText = it }, proximityMeters, detectedWidthPx, detectorFocalPx, detectedLabel, onApplyCalibration = {
                val known = knownText.replace(',', '.').toDoubleOrNull()
                if (known != null && known > 0 && points.size >= 2) {
                    val knownMeters = toMeters(known, unit)
                    val factor = knownMeters / distance(points[0], points[1])
                    if (factor in .5..2.0) {
                        correctionChange(factor)
                        message = "Calibración guardada"
                    } else message = "Calibración rechazada: repite los puntos"
                }
            })
            Spacer(Modifier.height(10.dp))
            Surface(color = Ink.copy(alpha=.88f), shape = RoundedCornerShape(25.dp)) {
                Column(Modifier.padding(15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ProximityStatus(proximityMeters)
                    Spacer(Modifier.height(4.dp))
                    Text(message, color = Color.White.copy(alpha=.72f), fontSize = 12.sp, maxLines = 1)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledIconButton(onClick = { points = emptyList() }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha=.14f), contentColor = Color.White)) { Icon(Icons.Outlined.RestartAlt, "Reiniciar") }
                        Spacer(Modifier.width(10.dp))
                        Button(onClick = {
                            val f = frame ?: return@Button
                            val display = context.resources.displayMetrics
                            val hit = f.hitTest(display.widthPixels / 2f, display.heightPixels / 2f)
                                .firstOrNull { it.trackable.trackingState == com.google.ar.core.TrackingState.TRACKING }
                            if (hit == null) message = "No se detectó una superficie. Muévete y vuelve a intentar"
                            else {
                                val limit = if (mode == MeasureMode.AREA) Int.MAX_VALUE else 2
                                points = (if (points.size >= limit) emptyList() else points) + hit.hitPose.point()
                                message = if (points.size + 1 >= limit) "Medición lista" else "Punto ${('A'.code + points.size).toChar()} guardado"
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink), modifier = Modifier.weight(1f).height(52.dp)) {
                            Icon(Icons.Outlined.AddLocationAlt, null); Spacer(Modifier.width(7.dp)); Text("Marcar", fontWeight = FontWeight.Bold)
                        }
                        if (mode == MeasureMode.AREA && points.size >= 3) { Spacer(Modifier.width(8.dp)); FilledIconButton(onClick = { message = "Área cerrada" }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)) { Icon(Icons.Outlined.Check, null, tint = Ink) } }
                    }
                }
            }
        }
    }
}

@Composable private fun ResultPanel(mode: MeasureMode, points: List<Point3>, correction: Double, unit: MeasureUnit, knownText: String, knownChange: (String)->Unit, proximityMeters: Double?, detectedWidthPx: Double?, detectorFocalPx: Double?, detectedLabel: String, onApplyCalibration: ()->Unit) {
    Surface(color = Color.White.copy(alpha=.96f), shape = RoundedCornerShape(24.dp), shadowElevation = 5.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(mode.title.uppercase(), fontSize=10.sp, fontWeight=FontWeight.Bold, color=Slate)
                Spacer(Modifier.weight(1f))
                proximityMeters?.let { Text("%.2f m de distancia".format(it), fontSize=11.sp, color=Slate) }
            }
            when (mode) {
                MeasureMode.DISTANCE -> BigValue(if(points.size<1) "Marca el punto A" else if(points.size<2) "Marca el punto B" else formatLength(distance(points[0], points[1]) * correction, unit))
                MeasureMode.OBJECT -> {
                    val depth = proximityMeters
                    val width = detectedWidthPx
                    val focal = detectorFocalPx
                    val estimate = if (depth != null && width != null && focal != null && focal > 0) width * depth / focal * correction else null
                    BigValue(estimate?.let { formatLength(it, unit) } ?: "Detectando…")
                    Text(detectedLabel, color = Slate, fontSize = 12.sp)
                    Text("Estimación por profundidad + óptica", color = Color.Gray, fontSize = 11.sp)
                }
                MeasureMode.RECTANGLE -> if(points.size<2) BigValue("Marca la esquina opuesta") else {
                    val (w,h,a)=rectangleMetrics(points[0],points[1]); BigValue(formatArea(a*correction*correction, unit)); Text("Ancho ${formatLength(w*correction,unit)}  ·  Alto ${formatLength(h*correction,unit)}", color=Color.Gray)
                }
                MeasureMode.AREA -> { BigValue(if(points.size<3) "Faltan ${3-points.size} puntos" else formatArea(polygonArea(points)*correction*correction,unit)); if(points.size>=2) Text("Perímetro ${formatLength(perimeter(points, points.size>=3)*correction,unit)}", color=Color.Gray) }
                MeasureMode.CALIBRATE -> {
                    BigValue(if(points.size<2) "Marca B" else "Medido ${formatLength(distance(points[0],points[1]),unit)}")
                    Row(verticalAlignment=Alignment.CenterVertically) { OutlinedTextField(knownText, knownChange, label={Text("Medida real (${unit.short})")}, singleLine=true, modifier=Modifier.weight(1f)); Spacer(Modifier.width(8.dp)); Button(onClick=onApplyCalibration, enabled=points.size>=2){Text("Guardar")} }
                }
            }
        }
    }
}

@Composable private fun BigValue(text: String) = Text(text, fontSize=25.sp, fontWeight=FontWeight.Bold, color=Ink)
private fun formatLength(m: Double, unit: MeasureUnit): String { val v=when(unit){MeasureUnit.METERS->m;MeasureUnit.CENTIMETERS->m*100;MeasureUnit.FEET->m*3.280839895;MeasureUnit.INCHES->m*39.37007874}; return String.format(Locale.getDefault(),if(v<10)"%.2f %s" else "%.1f %s",v,unit.short) }
private fun formatArea(m2:Double,unit:MeasureUnit):String { val v=when(unit){MeasureUnit.METERS->m2;MeasureUnit.CENTIMETERS->m2*10000;MeasureUnit.FEET->m2*10.7639104;MeasureUnit.INCHES->m2*1550.0031}; return String.format(Locale.getDefault(),"%.2f %s²",v,unit.short) }
private fun toMeters(value: Double, unit: MeasureUnit): Double = when(unit) { MeasureUnit.METERS -> value; MeasureUnit.CENTIMETERS -> value / 100.0; MeasureUnit.FEET -> value / 3.280839895; MeasureUnit.INCHES -> value / 39.37007874 }

@Composable private fun ProximityStatus(distance: Double?) {
    val (label, color) = when {
        distance == null -> "BUSCANDO SUPERFICIE" to Color(0xFFFFC857)
        distance < .80 -> "ALÉJATE UN POCO" to Color(0xFFFFC857)
        distance <= .95 -> "DISTANCIA ÓPTIMA" to Mint
        else -> "ACÉRCATE AL OBJETO" to Color(0xFFFF8A80)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun Crosshair() { Canvas(Modifier.fillMaxSize()) { val c=center; drawCircle(Mint, 18.dp.toPx(), c, style=Stroke(2.dp.toPx())); drawLine(Mint,c-Offset(28.dp.toPx(),0f),c+Offset(28.dp.toPx(),0f),2.dp.toPx(),StrokeCap.Round); drawLine(Mint,c-Offset(0f,28.dp.toPx()),c+Offset(0f,28.dp.toPx()),2.dp.toPx(),StrokeCap.Round) } }

@Composable private fun GpsMeasure(unit:MeasureUnit, unitChange:(MeasureUnit)->Unit, onBack: () -> Unit) {
    val context=LocalContext.current
    val tracker=remember { GpsTracker(context.applicationContext) }
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ permission=it[Manifest.permission.ACCESS_FINE_LOCATION]==true }
    DisposableEffect(Unit){ onDispose { tracker.stop() } }
    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding().padding(22.dp), horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically){ IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,null,tint=Color.White)}; Text("Recorrido GPS", color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold) }
        Spacer(Modifier.height(30.dp)); UnitSelector(unit,unitChange,dark=true)
        Spacer(Modifier.weight(.35f)); Text(if(tracker.running) "MIDIENDO DESDE EL PUNTO A" else "LISTO PARA COMENZAR", color=Mint,fontSize=12.sp,fontWeight=FontWeight.Bold)
        Text(formatLength(tracker.distanceMeters,unit),color=Color.White,fontSize=58.sp,fontWeight=FontWeight.Light)
        Text("DISTANCIA",color=Color.White.copy(alpha=.45f),fontSize=11.sp)
        Spacer(Modifier.height(35.dp)); Stat("PRECISIÓN GPS",tracker.accuracyMeters?.let{"±${it.toInt()} m"}?:"Esperando señal")
        Spacer(Modifier.weight(1f)); Text("Para recorridos exteriores. Espera una precisión menor de ±20 m antes de iniciar.",color=Color.White.copy(alpha=.55f),fontSize=12.sp)
        Spacer(Modifier.height(18.dp)); Button(onClick={ if(!permission) launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)) else if(tracker.running) tracker.stop() else tracker.start() }, modifier=Modifier.fillMaxWidth().height(62.dp), colors=ButtonDefaults.buttonColors(containerColor=if(tracker.running) Color(0xFFFF718B) else Mint,contentColor=Ink),shape=RoundedCornerShape(20.dp)){ Icon(if(tracker.running) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,null);Spacer(Modifier.width(8.dp));Text(if(tracker.running)"DETENER EN PUNTO B" else "INICIAR EN PUNTO A",fontWeight=FontWeight.Bold) }
    }
}

@Composable private fun Stat(label:String,value:String){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(value,color=Color.White,fontWeight=FontWeight.SemiBold);Text(label,color=Color.White.copy(alpha=.4f),fontSize=10.sp)}}

@Composable private fun UnitSelector(unit:MeasureUnit,onChange:(MeasureUnit)->Unit,dark:Boolean=false){
    var open by remember{mutableStateOf(false)}
    Box{ OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.outlinedButtonColors(contentColor=if(dark)Color.White else Ink)){Icon(Icons.Outlined.Straighten,null);Spacer(Modifier.width(8.dp));Text("Unidad: ${unit.label}");Spacer(Modifier.weight(1f));Icon(Icons.Outlined.ExpandMore,null)}
        DropdownMenu(open,{open=false}){MeasureUnit.entries.forEach{DropdownMenuItem({Text(it.label)},{onChange(it);open=false})}}
    }
}
