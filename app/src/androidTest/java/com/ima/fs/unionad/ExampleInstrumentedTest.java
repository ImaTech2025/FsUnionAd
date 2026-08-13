package com.ima.fs.unionad;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/**
 * Instrumented test, which will execute on an Android device.
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    @Test
    public void useAppContext() {
        // Context of the app under test.
        android.content.Context appContext =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.ima.fs.unionad", appContext.getPackageName());
    }
}
