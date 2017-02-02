# libMediaAmbilight

Demo application with libMediaAmbilight using on Android.
Android is connected to 4 Bluetooth leds (Giec leds).

[![Demo](https://j.gifs.com/O7oP0E.gif)](https://youtu.be/R-9KNaHqQ1Y)


Usage:

Ambilight.getInstance(View view, Context context, SurfaceHolder.Callback callback, Callable<Boolean> preparedCallback, Boolean debug);
Surface surface = Ambilight.getSurface();

MediaPlayer mp = new MediaPlayer();
mp.setSurface(Ambilight.getSurface());
