package com.andbrowser.library.vie;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

import bg.cytec.android.fskmodem.FSKConfig;
import bg.cytec.android.fskmodem.FSKDecoder;
import bg.cytec.android.fskmodem.FSKEncoder;

public class VieLedIntentService extends IntentService {

    public static final String EXTRA_COLOR = "COLOR";

    public static final String RED = "r";
    public static final String GREEN = "g";
    public static final String BLUE = "b";
    public static final String YELLOW = "y";
    public static final String WHITE = "w";
    public static final String CLEAR = "c";

    CountDownLatch doneSignal;

    static String ENCODER_DATA;

    private AudioTrack mAudioTrack;
    protected FSKConfig mConfig;
    protected FSKEncoder mEncoder;
    protected FSKDecoder mDecoder;

    protected Runnable mDataFeeder = new Runnable() {

        @Override
        public void run() {
            byte[] data = ENCODER_DATA.getBytes();

            if (data.length > FSKConfig.ENCODER_DATA_BUFFER_SIZE) {
                //chunk data

                byte[] buffer = new byte[FSKConfig.ENCODER_DATA_BUFFER_SIZE];

                ByteBuffer dataFeed = ByteBuffer.wrap(data);

                while (dataFeed.remaining() > 0) {

                    if (dataFeed.remaining() < buffer.length) {
                        buffer = new byte[dataFeed.remaining()];
                    }

                    dataFeed.get(buffer);

                    mEncoder.appendData(buffer);

                    try {
                        Thread.sleep(100); //wait for encoder to do its job, to avoid buffer overflow and data rejection
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            else {
                mEncoder.appendData(data);
            }
        }
    };

    /**
     * Create Intent instance.
     *
     * @param context
     * @param color
     * @return
     */
    public static Intent createIntent(final Context context, final String color) {
        Intent intent = new Intent(context, VieLedIntentService.class);
        intent.putExtra(EXTRA_COLOR, color);

        return intent;
    }



    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     */
    public VieLedIntentService() {
        super("VieIntentService");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            mConfig = new FSKConfig(FSKConfig.SAMPLE_RATE_44100, FSKConfig.PCM_8BIT, FSKConfig.CHANNELS_MONO, FSKConfig.SOFT_MODEM_MODE_9, FSKConfig.THRESHOLD_1P);
        } catch (IOException e1) {
            e1.printStackTrace();
            throw  new RuntimeException("mConfig EXCEPTION");
        }

        /// INIT FSK DECODER
        mDecoder = new FSKDecoder(mConfig, new FSKDecoder.FSKDecoderCallback() {

            @Override
            public void decoded(byte[] newData) {
                // do nothing
            }
        });

        /// INIT FSK ENCODER
        mEncoder = new FSKEncoder(mConfig, new FSKEncoder.FSKEncoderCallback() {

            @Override
            public void encoded(byte[] pcm8, short[] pcm16) {
                if (mConfig.pcmFormat == FSKConfig.PCM_8BIT) {
                    //8bit buffer is populated, 16bit buffer is null

                    for(int i=0; i<20; i++) {
                        mAudioTrack.write(pcm8, 0, pcm8.length);
                        try {
                            Thread.sleep(30); //wait for encoder to do its job, to avoid buffer overflow and data rejection
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    mDecoder.appendSignal(pcm8);
                    Log.e("TAG", "encoded(): after audioTrack.write()");
                    doneSignal.countDown();
                }
                else if (mConfig.pcmFormat == FSKConfig.PCM_16BIT) {
                    //16bit buffer is populated, 8bit buffer is null

                    mAudioTrack.write(pcm16, 0, pcm16.length);

                    mDecoder.appendSignal(pcm16);
                }
            }
        });

        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                mConfig.sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_8BIT, 1024,
                AudioTrack.MODE_STREAM);

        mAudioTrack.play();


    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        doneSignal = new CountDownLatch(1);
        ENCODER_DATA = intent.getStringExtra(EXTRA_COLOR);
        new Thread(mDataFeeder).start();
        Log.d("TAG", "onHandleIntent(): " + ENCODER_DATA);
        try {
            doneSignal.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException("await() is exception.");
        }
        Log.d("TAG", "after await(): ");
    }

    @Override
    public void onDestroy() {
        mDecoder.stop();

        mEncoder.stop();

        mAudioTrack.stop();
        mAudioTrack.release();

        super.onDestroy();
    }

}
