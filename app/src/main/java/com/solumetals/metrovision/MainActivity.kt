package com.solumetals.metrovision

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.ar.core.Frame
import io.github.sceneview.ar.ARSceneView
import java.util.Locale

private val Ink = Color(0xFF071815)
private val Mint = Color(0xFF00D8B4)
private val Fog = Color(0xFFF2F7F5)

enum class Page { HOME, CAMERA, GPS }
enum class MeasureMode(val title: String) {
    DISTANCE("Distancia"), RECTANGLE("Rectángulo"), AREA("Área libre"), CALIBRATE("Calibrar")
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
    MaterialTheme(colorScheme = lightColorScheme(primary = Ink, secondary = Mint, background = Fog), content = content)
}

@Composable private fun MetroApp() {
    var page by remember { mutableStateOf(Page.HOME) }
    var mode by remember { mutableStateOf(MeasureMode.DISTANCE) }
    var unit by remember { mutableStateOf(MeasureUnit.METERS) }
    when (page) {
        Page.HOME -> Home(unit, { unit = it }, onCamera = { mode = it; page = Page.CAMERA }, onGps = { page = Page.GPS })
        Page.CAMERA -> CameraMeasure(mode, unit, { unit = it }, onBack = { page = Page.HOME })
        Page.GPS -> GpsMeasure(unit, { unit = it }, onBack = { page = Page.HOME })
    }
}

@Composable private fun Home(unit: MeasureUnit, unitChange: (MeasureUnit)->Unit, onCamera: (MeasureMode) -> Unit, onGps: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Fog).padding(22.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(28.dp))
        Text("MetroVision", fontSize = 31.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Mide espacios, objetos y recorridos", color = Color(0xFF61706C))
        Spacer(Modifier.height(18.dp)); UnitSelector(unit, unitChange)
        Spacer(Modifier.height(30.dp))
        FeatureCard(Icons.Outlined.Straighten, "Distancia", "Marca los puntos A y B", Mint) { onCamera(MeasureMode.DISTANCE) }
        FeatureCard(Icons.Outlined.CropSquare, "Pared o rectángulo", "Ancho, alto, área y perímetro", Color(0xFF7BA6FF)) { onCamera(MeasureMode.RECTANGLE) }
        FeatureCard(Icons.Outlined.Pentagon, "Área libre", "Marca todos los vértices", Color(0xFFFFB45B)) { onCamera(MeasureMode.AREA) }
        FeatureCard(Icons.Outlined.Tune, "Calibración", "Corrige usando una medida conocida", Color(0xFFA98BFF)) { onCamera(MeasureMode.CALIBRATE) }
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

@Composable private fun CameraMeasure(mode: MeasureMode, unit: MeasureUnit, unitChange: (MeasureUnit)->Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    LaunchedEffect(Unit) { if (!permission) launcher.launch(Manifest.permission.CAMERA) }
    var frame by remember { mutableStateOf<Frame?>(null) }
    var points by remember { mutableStateOf(listOf<Point3>()) }
    var correction by remember { mutableDoubleStateOf(1.0) }
    var knownText by remember { mutableStateOf("1.00") }
    var message by remember { mutableStateOf("Mueve lentamente el teléfono para detectar la superficie") }

    Box(Modifier.fillMaxSize().background(Ink)) {
        if (permission) ARSceneView(Modifier.fillMaxSize(), planeRenderer = true, onSessionUpdated = { _, f -> frame = f })
        else Text("Se necesita permiso de cámara", color = Color.White, modifier = Modifier.align(Alignment.Center))
        Crosshair()
        Row(Modifier.statusBarsPadding().padding(12.dp).align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, colors = IconButtonDefaults.iconButtonColors(containerColor = Ink.copy(alpha=.72f))) { Icon(Icons.Outlined.ArrowBack, null, tint = Color.White) }
            Spacer(Modifier.width(8.dp)); Surface(color = Ink.copy(alpha=.72f), shape = RoundedCornerShape(18.dp)) { Text(mode.title, color = Color.White, modifier = Modifier.padding(horizontal=15.dp, vertical=10.dp), fontWeight = FontWeight.SemiBold) }
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(14.dp)) {
            ResultPanel(mode, points, correction, unit, knownText, { knownText = it }, onApplyCalibration = {
                val known = knownText.replace(',', '.').toDoubleOrNull()
                if (known != null && known > 0 && points.size >= 2) correction = known / distance(points[0], points[1])
            })
            Spacer(Modifier.height(10.dp))
            Surface(color = Ink.copy(alpha=.88f), shape = RoundedCornerShape(25.dp)) {
                Column(Modifier.padding(15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(message, color = Color.White.copy(alpha=.78f), fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { points = emptyList(); correction = 1.0 }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Icon(Icons.Outlined.RestartAlt, null); Spacer(Modifier.width(5.dp)); Text("Reiniciar") }
                        Spacer(Modifier.width(14.dp))
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
                        }, colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink), modifier = Modifier.height(52.dp)) {
                            Icon(Icons.Outlined.AddLocationAlt, null); Spacer(Modifier.width(7.dp)); Text("Marcar punto", fontWeight = FontWeight.Bold)
                        }
                        if (mode == MeasureMode.AREA && points.size >= 3) { Spacer(Modifier.width(8.dp)); FilledIconButton(onClick = { message = "Área cerrada" }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)) { Icon(Icons.Outlined.Check, null, tint = Ink) } }
                    }
                }
            }
        }
    }
}

@Composable private fun ResultPanel(mode: MeasureMode, points: List<Point3>, correction: Double, unit: MeasureUnit, knownText: String, knownChange: (String)->Unit, onApplyCalibration: ()->Unit) {
    if (points.isEmpty()) return
    Surface(color = Color.White, shape = RoundedCornerShape(23.dp), shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("${points.size} punto${if(points.size==1)"" else "s"}", fontSize=12.sp, color=Color.Gray)
            when (mode) {
                MeasureMode.DISTANCE -> BigValue(if(points.size<2) "Marca B" else formatLength(distance(points[0], points[1]) * correction, unit))
                MeasureMode.RECTANGLE -> if(points.size<2) BigValue("Marca la esquina opuesta") else {
                    val (w,h,a)=rectangleMetrics(points[0],points[1]); BigValue(formatArea(a*correction*correction, unit)); Text("Ancho ${formatLength(w*correction,unit)}  ·  Alto ${formatLength(h*correction,unit)}", color=Color.Gray)
                }
                MeasureMode.AREA -> { BigValue(if(points.size<3) "Faltan ${3-points.size} puntos" else formatArea(polygonArea(points)*correction*correction,unit)); if(points.size>=2) Text("Perímetro ${formatLength(perimeter(points, points.size>=3)*correction,unit)}", color=Color.Gray) }
                MeasureMode.CALIBRATE -> {
                    BigValue(if(points.size<2) "Marca B" else "Medido ${formatLength(distance(points[0],points[1]),unit)}")
                    Row(verticalAlignment=Alignment.CenterVertically) { OutlinedTextField(knownText, knownChange, label={Text("Distancia real (m)")}, singleLine=true, modifier=Modifier.weight(1f)); Spacer(Modifier.width(8.dp)); Button(onClick=onApplyCalibration, enabled=points.size>=2){Text("Aplicar")} }
                }
            }
        }
    }
}

@Composable private fun BigValue(text: String) = Text(text, fontSize=25.sp, fontWeight=FontWeight.Bold, color=Ink)
private fun formatLength(m: Double, unit: MeasureUnit): String { val v=when(unit){MeasureUnit.METERS->m;MeasureUnit.CENTIMETERS->m*100;MeasureUnit.FEET->m*3.280839895;MeasureUnit.INCHES->m*39.37007874}; return String.format(Locale.getDefault(),if(v<10)"%.2f %s" else "%.1f %s",v,unit.short) }
private fun formatArea(m2:Double,unit:MeasureUnit):String { val v=when(unit){MeasureUnit.METERS->m2;MeasureUnit.CENTIMETERS->m2*10000;MeasureUnit.FEET->m2*10.7639104;MeasureUnit.INCHES->m2*1550.0031}; return String.format(Locale.getDefault(),"%.2f %s²",v,unit.short) }

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
