# Workers are created by reflection by WorkManager.
-keep class com.bootybay.hallowmiss.work.** extends androidx.work.ListenableWorker { <init>(...); }
