package com.khedr.smartassistant

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Bundle
import android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.khedr.smartassistant.data.SecureApiKeyStore
import com.khedr.smartassistant.ai.GeminiFlashClient
import com.khedr.smartassistant.data.AssistantStore
import com.khedr.smartassistant.service.FloatingBubbleService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { AssistantScreen() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AssistantScreen() {
    val ctx = LocalContext.current
    val store = remember { AssistantStore(ctx) }
    var enabled by remember { mutableStateOf(store.enabled()) }
    var auto by remember { mutableStateOf(store.autoSend()) }
    var contact by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf("") }
    var replyText by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf(store.allowedContacts().toList()) }
    var selected by remember { mutableStateOf(contacts.firstOrNull().orEmpty()) }
    var canned by remember { mutableStateOf(selected.takeIf { it.isNotBlank() }?.let { store.replies(it).joinToString("\n") } ?: "") }
    val keyStore = remember { SecureApiKeyStore(ctx) }
    var apiKey by remember { mutableStateOf("") }
    var keyStatus by remember { mutableStateOf(if (keyStore.read().isNullOrBlank()) "لم يتم حفظ مفتاح Gemini" else "يوجد مفتاح محفوظ (مخفي)") }
    var keyBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("مساعد واتساب الذكي") }) }) { pad ->
            LazyColumn(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("Gemini Flash — إعداد الذكاء الاصطناعي", style = MaterialTheme.typography.titleLarge) }
                item { Text("أدخل مفتاح Google AI Studio. سيُختبر الاتصال أولاً ثم يُحفظ المفتاح مشفراً على الجهاز.", style = MaterialTheme.typography.bodyMedium) }
                item { OutlinedTextField(apiKey, { apiKey=it }, label={Text("Gemini API Key")}, visualTransformation=PasswordVisualTransformation(), singleLine=true, modifier=Modifier.fillMaxWidth()) }
                item { Text(keyStatus, color=if(keyStatus.contains("نجاح") || keyStatus.contains("محفوظ")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant) }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(enabled=!keyBusy && apiKey.isNotBlank(), onClick={ keyBusy=true; keyStatus="جارٍ فحص المفتاح..."; scope.launch { val result=withContext(Dispatchers.IO){GeminiFlashClient.test(apiKey.trim())}; keyBusy=false; if(result.success){keyStore.save(apiKey.trim()); apiKey=""; keyStatus="نجاح الاتصال؛ تم حفظ المفتاح بأمان."}else keyStatus=result.message } }, modifier=Modifier.weight(1f)) { Text(if(keyBusy) "جارٍ الفحص" else "فحص وحفظ المفتاح") }
                    OutlinedButton(enabled=!keyBusy, onClick={keyStore.clear();apiKey="";keyStatus="تم حذف المفتاح المحفوظ."}, modifier=Modifier.weight(1f)) { Text("حذف المفتاح") }
                } }
                item { Text("النموذج: ${GeminiFlashClient.MODEL} • يحتاج مفتاحاً صالحاً وحصة API متاحة. لا يوجد مفتاح مجاني عام؛ المستخدم يضيف مفتاحه الخاص.", style=MaterialTheme.typography.bodySmall) }
                item { Text("التحكم بالمساعد", style = MaterialTheme.typography.titleLarge) }
                item { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Switch(enabled, { enabled=it; store.setEnabled(it) }); Spacer(Modifier.width(8.dp)); Text(if(enabled) "المساعد يعمل" else "المساعد متوقف") } }
                item { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Switch(auto, { auto=it; store.setAutoSend(it) }); Spacer(Modifier.width(8.dp)); Text(if(auto) "إرسال تلقائي (قد يرسل ردوداً دون مراجعة)" else "طلب موافقة قبل الإرسال") } }
                item { Button(onClick = { ctx.startActivity(Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, modifier=Modifier.fillMaxWidth()) { Text("منح صلاحية قراءة الإشعارات") } }
                item { Button(onClick = { if (!Settings.canDrawOverlays(ctx)) ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))) else ctx.startService(Intent(ctx, FloatingBubbleService::class.java)) }, modifier=Modifier.fillMaxWidth()) { Text("السماح بالأيقونة العائمة / تشغيلها") } }
                item { Divider(); Text("جهات الاتصال المسموح بها", style=MaterialTheme.typography.titleLarge) }
                item { OutlinedTextField(contact, { contact=it }, label={Text("اسم جهة الاتصال كما يظهر في واتساب")}, modifier=Modifier.fillMaxWidth()) }
                item { Button(onClick={ val n=contact.trim(); if(n.isNotEmpty()){store.addContact(n); contacts=store.allowedContacts().toList(); selected=n; contact=""; profile=store.profile(n); canned=store.replies(n).joinToString("\n") } }, modifier=Modifier.fillMaxWidth()) { Text("إضافة جهة اتصال") } }
                items(contacts) { name -> Row(Modifier.fillMaxWidth(), verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) { RadioButton(selected==name,{ selected=name; profile=store.profile(name); canned=store.replies(name).joinToString("\n") }); Text(name, Modifier.weight(1f)); TextButton(onClick={store.removeContact(name); contacts=store.allowedContacts().toList(); if(selected==name) selected=contacts.firstOrNull().orEmpty()}) { Text("حذف") } } }
                if(selected.isNotBlank()) {
                    item { Divider(); Text("ملف $selected", style=MaterialTheme.typography.titleLarge) }
                    item { OutlinedTextField(profile,{profile=it},label={Text("العلاقة والأسلوب والمعلومات")},modifier=Modifier.fillMaxWidth(),minLines=3) }
                    item { OutlinedTextField(canned,{canned=it},label={Text("رد جاهز في كل سطر")},modifier=Modifier.fillMaxWidth(),minLines=4) }
                    item { Button(onClick={store.setProfile(selected,profile);store.setReplies(selected,canned.lines().filter{it.isNotBlank()})},modifier=Modifier.fillMaxWidth()) { Text("حفظ بيانات جهة الاتصال") } }
                }
                item { Divider(); Text("معاينة الرد", style=MaterialTheme.typography.titleLarge) }
                item { OutlinedTextField(replyText,{replyText=it},label={Text("اكتب رسالة لمراجعة صياغتها")},modifier=Modifier.fillMaxWidth(),minLines=2) }
                item { Button(onClick={ replyText=replyText.replace(Regex("[ ]{2,}")," ").replace(Regex("[😂🤣]{3,}"),"😂").trim() },modifier=Modifier.fillMaxWidth()) { Text("تنقيح سريع للرد") } }
                item { Text("ملاحظة: عند حفظ مفتاح Gemini، يستخدمه المساعد لتوليد الردود. الإرسال التلقائي يعتمد على توفر إجراء الرد السريع في إشعار واتساب.", style=MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
