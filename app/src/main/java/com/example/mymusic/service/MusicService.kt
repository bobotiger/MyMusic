package com.example.mymusic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.mymusic.MainActivity
import com.example.mymusic.R

class MusicService : Service(){

    private var mediaPlayer:MediaPlayer?=null
    private val CHANNEL_ID = "MusicServiceChannel"
    private var isPrepared = false // 添加一个标志来标记MediaPlayer是否准备好
    private var isPaused: Boolean = false // 追踪播放器是否处于暂停状态

    companion object{
        const val ACTION_PLAY = "action.PLAY"
        const val ACTION_PAUSE = "action.PAUSE"
        const val ACTION_RESUME = "action.RESUME"
        const val ACTION_STOP = "action.STOP"
        const val ACTION_UPDATE_PROGRESS = "action.UPDATE_PROGRESS"
    }

    // 使用一个mediaSession兼容库以更方便地处理媒体播放通知
    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this,"MusicServiceMediaSession")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotification(){
        createNotficationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags)

        val playIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PLAY }
        val pendingPlay = PendingIntent.getService(this, 0, playIntent, flags)

        val pauseIntent = Intent(this, MusicService::class.java).apply { action = ACTION_PAUSE }
        val pendingPause = PendingIntent.getService(this, 1, pauseIntent, flags)

        val resumeIntent = Intent(this, MusicService::class.java).apply { action = ACTION_RESUME }
        val pendingResume = PendingIntent.getService(this, 2, resumeIntent, flags)

        val stopIntent = Intent(this, MusicService::class.java).apply { action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(this, 3, stopIntent, flags)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Music Service")
            .setContentText("Playing music...")
            .setSmallIcon(R.drawable.music_note)
            .setContentIntent(pendingIntent)
//            .addAction(R.drawable.music_play, "Play", pendingPlay)
            .addAction(R.drawable.music_pause, "Pause", pendingPause)
            .addAction(R.drawable.music_resume, "Resume", pendingResume)
            .addAction(R.drawable.music_stop, "Stop", pendingStop)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSession.sessionToken))
            .build()

        startForeground(1,notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val url = intent.getStringExtra("URL") ?: return START_NOT_STICKY
                playMusic(url)
                createNotification()
            }
            ACTION_PAUSE -> pauseMusic()
            ACTION_RESUME -> resumeMusic()
            ACTION_STOP -> stopAndReleaseMediaPlayer(true)
            else -> {
                // 处理其他动作，例如播放新曲目
                val url = intent?.getStringExtra("URL") ?: ""
                if (url.isNotBlank()) playMusic(url)
            }
        }
        return START_NOT_STICKY
    }

    fun playMusic(url:String){
        stopAndReleaseMediaPlayer(false)

        mediaPlayer = MediaPlayer().apply {
            setDataSource(url)
            prepareAsync()
            setOnPreparedListener{
                isPrepared = true // 当MediaPlayer准备好时设置标志
                start()
                createNotification()
            }
            setOnErrorListener { mp, what, extra ->
                Log.e("MusicService","Playback Error - what:$what extra: $extra")
                isPrepared = false
//                stopAndReleaseMediaPlayer()
                true
            }
        }
    }

    fun pauseMusic() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            isPaused = true // 标记为暂停状态
//            createNotification()
        }
    }

    fun resumeMusic() {
        if (isPaused && mediaPlayer != null) {
            mediaPlayer?.start()
            isPaused = false // 标记为非暂停状态，即正在播放
//            createNotification()
        }
    }

    private fun stopAndReleaseMediaPlayer(stopService:Boolean) {
        if (mediaPlayer?.isPlaying == true || isPrepared) {
            mediaPlayer?.stop()
        }
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        if (stopService) stopForeground(true)
    }

    private fun createNotficationChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Music Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for Music Service"
            }
            val manager:NotificationManager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            Log.d("MusicService", "Notification channel created") // 添加日志
        }else{
            Log.d("MusicService", "Notification channel not created. Android version < Oreo")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAndReleaseMediaPlayer(true)
        mediaSession.release()
    }
}