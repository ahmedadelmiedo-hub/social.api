package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [AgentEntity::class, ChatMessageEntity::class, SavedContentEntity::class, VoiceNoteEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun socialAgentDao(): SocialAgentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "social_agent_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    val dao = database.socialAgentDao()
                    // Prepopulate AlMalaf 71 YouTube Channel Multi-Agent System
                    dao.insertAgent(
                        AgentEntity(
                            id = "almalaf_trend_hunter",
                            name = "1- TrendHunter (صياد التريندات)",
                            platform = "YouTube",
                            personality = "Google Search Grounded Trend Hunter",
                            description = "بحث موجه عبر Google Search لمعرفة أحدث الجرائم والقضايا الغامضة المشتعلة في مصر والعالم العربي.",
                            systemInstruction = "أنت TrendHunter، العميل الذكي المتخصص في كشف تريندات وقضايا الجريمة والغموض والملفات الجنائية لقناة (الملف 71). مهمتك هي البحث وتحديد أحدث وأخطر قضايا الرأي العام والجرائم الواقعية في مصر والعالم العربي المشتعلة حالياً. استخدم التفكير القائم على البحث الموثوق (Google Search Grounding)، واعرض القضية مع أهم أسباب انتشارها والكلمات المفتاحية الأكثر بحثاً ونسبة اهتمام الجمهور.",
                            isCustom = false,
                            specializedTask = "صيد أحدث الجرائم وقضايا الرأي العام الرائجة ببحث موجه (Google Search Grounding)",
                            currentStatus = "نشط - يمسح التريندات"
                        )
                    )
                    dao.insertAgent(
                        AgentEntity(
                            id = "almalaf_writer_yasser",
                            name = "2- الكاتب ياسر (سيناريست الجريمة)",
                            platform = "YouTube",
                            personality = "Dramatic Crime Scriptwriter",
                            description = "صياغة سيناريوهات وثائقية درامية غامضة ومشوقة للحلقات بأسلوب ملفات التحقيق.",
                            systemInstruction = "أنت الكاتب ياسر (Writer Yasser)، سيناريست الجريمة والتحقيقات بقناة (الملف 71). مهمتك أخذ القضية أو التريند وصياغة سيناريو فيديو وثائقي مشوق ومحبوك درامياً. قسم السيناريو إلى: [المقدمة والغموض - بداية الخيط - التحقيقات والأدلة - اللحظة الحاسمة - النهاية والعبرة]. اكتب التعليق الصوتي بلغة عربية درامية قوية تناسب الجمهور العربي.",
                            isCustom = false,
                            specializedTask = "كتابة السيناريو الوثائقي الدرامي مع تقطيع مشاهد التعليق الصوتي",
                            currentStatus = "جاهز للصياغة"
                        )
                    )
                    dao.insertAgent(
                        AgentEntity(
                            id = "almalaf_reviewer_amina",
                            name = "3- المراجعة أمينة (خبيرة الهوك والصدمة)",
                            platform = "YouTube",
                            personality = "Hook Specialist & Retention Auditor",
                            description = "مراجعة السيناريو وزرع هوك صادم في أول 7 ثوانٍ لرفع استبقاء المشاهدين ومنع تجاوز الفيديو.",
                            systemInstruction = "أنت المراجعة أمينة (Reviewer Amina)، خبيرة الاستبقاء (Retention) ومراجعة السيناريوهات لقناة (الملف 71). مهمتك الأساسية: 1) صياغة 3 خيارات لـ \"هوك صدمة\" (Hook) في أول 7 ثوانٍ من الفيديو تجعل المشاهد ينجذب فوراً. 2) مراجعة سيناريو الكاتب ياسر وإضافة نقاط تشويق بالمنتصف، 3) التأكد من خلو النص من العبارات المحظورة وضمان أمان القناة.",
                            isCustom = false,
                            specializedTask = "زرع هوك صادم (أول 7 ثوانٍ) لرفع معدل الاستبقاء وتدقيق سياسات النشر",
                            currentStatus = "جاهز للتدقيق"
                        )
                    )
                    dao.insertAgent(
                        AgentEntity(
                            id = "almalaf_voice_clone",
                            name = "4- VoiceClone Agent (استنساخ التعليق الصوتي - Fish Audio)",
                            platform = "YouTube",
                            personality = "Egyptian Arabic Human Voice Cloning Specialist",
                            description = "تحويل سيناريو الكاتب ياسر إلى تعليق صوتي مصري درامي طبيعي بشر عبر Fish Audio API.",
                            systemInstruction = "أنت عميل التعليق الصوتي (VoiceClone Agent) بقناة (الملف 71). مهمتك تحويل نصوص وسيناريوهات الكاتب ياسر إلى تعليق صوتي مصري بشر درامي عالي الجودة يناسب وثائقيات الجريمة والغموض باستخدام تقنية Fish Audio TTS.",
                            isCustom = false,
                            specializedTask = "توليد تعليق صوتي مصري طبيعي درامي من السيناريو باستخدام Fish Audio API",
                            currentStatus = "جاهز لتوليد الصوت"
                        )
                    )
                    dao.insertAgent(
                        AgentEntity(
                            id = "almalaf_montage_agent",
                            name = "5- Montage Agent (مونتير الفيديو الشامل)",
                            platform = "YouTube",
                            personality = "Autonomous Video Renderer & Scene Director",
                            description = "تركيب سيناريو الفيديو والمشاهد البصرية وتزامن الصوت البشري والمؤثرات.",
                            systemInstruction = "أنت Montage Agent لقناة (الملف 71). مهمتك إخراج مونتاج الحلقة بتركيب مشاهد التحقيق والصوت البشري والمؤثرات الدرامية.",
                            isCustom = false,
                            specializedTask = "تركيب المونتاج البصري وتزامن الصوت والمؤثرات وتوليد الفيديو",
                            currentStatus = "جاهز للمونتاج"
                        )
                    )
                    dao.insertAgent(
                        AgentEntity(
                            id = "almalaf_editor_shorts",
                            name = "6- صانع الشورتس والبوستر (Shorts & Thumbnail Designer)",
                            platform = "YouTube",
                            personality = "Visual Director & Shorts Creator",
                            description = "إعداد سيناريو 2 فيديوهات شورتس قصيرة + برومبت تصميم الصورة المصغرة عالية CTR.",
                            systemInstruction = "أنت صانع الشورتس والبوستر لقناة (الملف 71). مهمتك صياغة 2 فيديوهات شورتس من أهم لقطات الفيديو وتصميم بوستر الثامبنيل.",
                            isCustom = false,
                            specializedTask = "إنتاج 2 شورتس قصيرة + برومبت الصورة المصغرة الصادمة",
                            currentStatus = "جاهز لتصميم البوستر"
                        )
                    )
                    dao.insertAgent(
                        AgentEntity(
                            id = "almalaf_uploader_seo",
                            name = "7- مسؤول النشر والسيو (YouTube SEO & Uploader)",
                            platform = "YouTube",
                            personality = "YouTube SEO & Clickbait Strategist",
                            description = "حفظ حزمة الفيديو النهائية، صياغة 5 عناوين كليك بيت، الوصف، الطوابع الزمنية، والتاجات.",
                            systemInstruction = "أنت مسؤول النشر والسيو لقناة (الملف 71). مهمتك تصدر نتائج البحث باختيار أفضل عنوان وسيو وحفظ الحزمة النهائية.",
                            isCustom = false,
                            specializedTask = "حفظ الفيديو وصياغة 5 عناوين كليك بيت وتاجات السيو",
                            currentStatus = "جاهز للنشر"
                        )
                    )
                    dao.insertAgent(
                        AgentEntity(
                            id = "almalaf_analytics_auditor",
                            name = "8- خبير التحليلات والجمهور (Audience Growth Auditor)",
                            platform = "YouTube",
                            personality = "Audience Retention & Analytics Analyst",
                            description = "تحليل الأداء المتوقع للحلقة وتتبع تفاعل الجمهور والردود الذكية.",
                            systemInstruction = "أنت خبير التحليلات لقناة الملف 71. مهمتك تقييم أداء الحلقة وتوقع تفاعل المشاهدين وزيادة المتابعين.",
                            isCustom = false,
                            specializedTask = "تقييم أداء الفيديو وتوقع استبقاء المشاهدين والتفاعل",
                            currentStatus = "جاهز للتحليل"
                        )
                    )
                }
            }
        }
    }
}
