/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.piebridge.android.preference;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.xmlpull.v1.XmlPullParser;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.support.v4.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnKeyListener;
import android.widget.ListView;

/**
 * Shows a hierarchy of {@link Preference} objects as
 * lists. These preferences will
 * automatically save to {@link SharedPreferences} as the user interacts with
 * them. To retrieve an instance of {@link SharedPreferences} that the
 * preference hierarchy in this fragment will use, call
 * {@link PreferenceManager#getDefaultSharedPreferences(android.content.Context)}
 * with a context in the same package as this fragment.
 * <p>
 * Furthermore, the preferences shown will follow the visual style of system
 * preferences. It is easy to create a hierarchy of preferences (that can be
 * shown on multiple screens) via XML. For these reasons, it is recommended to
 * use this fragment (as a superclass) to deal with preferences in applications.
 * <p>
 * A {@link PreferenceScreen} object should be at the top of the preference
 * hierarchy. Furthermore, subsequent {@link PreferenceScreen} in the hierarchy
 * denote a screen break--that is the preferences contained within subsequent
 * {@link PreferenceScreen} should be shown on another screen. The preference
 * framework handles showing these other screens from the preference hierarchy.
 * <p>
 * The preference hierarchy can be formed in multiple ways:
 * <li> From an XML file specifying the hierarchy
 * <li> From different {@link Activity Activities} that each specify its own
 * preferences in an XML file via {@link Activity} meta-data
 * <li> From an object hierarchy rooted with {@link PreferenceScreen}
 * <p>
 * To inflate from XML, use the {@link #addPreferencesFromResource(int)}. The
 * root element should be a {@link PreferenceScreen}. Subsequent elements can point
 * to actual {@link Preference} subclasses. As mentioned above, subsequent
 * {@link PreferenceScreen} in the hierarchy will result in the screen break.
 * <p>
 * To specify an {@link Intent} to query {@link Activity Activities} that each
 * have preferences, use {@link #addPreferencesFromIntent}. Each
 * {@link Activity} can specify meta-data in the manifest (via the key
 * {@link PreferenceManager#METADATA_KEY_PREFERENCES}) that points to an XML
 * resource. These XML resources will be inflated into a single preference
 * hierarchy and shown by this fragment.
 * <p>
 * To specify an object hierarchy rooted with {@link PreferenceScreen}, use
 * {@link #setPreferenceScreen(PreferenceScreen)}.
 * <p>
 * As a convenience, this fragment implements a click listener for any
 * preference in the current hierarchy, see
 * {@link #onPreferenceTreeClick(PreferenceScreen, Preference)}.
 * 
 * <div class="special reference">
 * <h3>Developer Guides</h3>
 * <p>For information about using {@code PreferenceFragment},
 * read the <a href="{@docRoot}guide/topics/ui/settings.html">Settings</a>
 * guide.</p>
 * </div>
 *
 * <a name="SampleCode"></a>
 * <h3>Sample Code</h3>
 *
 * <p>The following sample code shows a simple preference fragment that is
 * populated from a resource.  The resource it loads is:</p>
 *
 * {@sample development/samples/ApiDemos/res/xml/preferences.xml preferences}
 *
 * <p>The fragment implementation itself simply populates the preferences
 * when created.  Note that the preferences framework takes care of loading
 * the current values out of the app preferences and writing them when changed:</p>
 *
 * {@sample development/samples/ApiDemos/src/com/example/android/apis/preference/FragmentPreferences.java
 *      fragment}
 *
 * @see Preference
 * @see PreferenceScreen
 */
public abstract class PreferenceFragment extends Fragment {

    private static final String PREFERENCES_TAG = "android:preferences";

    private PreferenceManager mPreferenceManager;
    private ListView mList;
    private boolean mHavePrefs;
    private boolean mInitDone;

    /**
     * The starting request code given out to preference framework.
     */
    private static final int FIRST_REQUEST_CODE = 100;

    private static final int MSG_BIND_PREFERENCES = 1;
    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case MSG_BIND_PREFERENCES:
                    bindPreferences();
                    break;
            }
        }
    };

    final private Runnable mRequestFocus = new Runnable() {
        public void run() {
            mList.focusableViewAvailable(mList);
        }
    };

    /**
     * Interface that PreferenceFragment's containing activity should
     * implement to be able to process preference items that wish to
     * switch to a new fragment.
     */
    public interface OnPreferenceStartFragmentCallback {
        /**
         * Called when the user has clicked on a Preference that has
         * a fragment class name associated with it.  The implementation
         * to should instantiate and switch to an instance of the given
         * fragment.
         */
        boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // FIXME: mPreferenceManager = new PreferenceManager(getActivity(), FIRST_REQUEST_CODE);
        mPreferenceManager = callConstructor(PreferenceManager.class,
            new Class[] { Activity.class, int.class },
            new Object[] { getActivity(), FIRST_REQUEST_CODE });
        // FIXME: mPreferenceManager.setFragment(this);
    }

    public static final String LAYOUT =
        "AwAIABgEAAABABwA0AEAABQAAAAAAAAAAAEAAGwAAAAAAAAAAAAAAA4AAAAeAAAALQAAADoAAAA/" +
        "AAAATwAAAFwAAABsAAAAegAAAIkAAACaAAAAqgAAAL4AAADPAAAA8gAAAPwAAAApAQAALAEAAEoB" +
        "AAALC29yaWVudGF0aW9uAA0NbGF5b3V0X2hlaWdodAAMDGxheW91dF93aWR0aAAKCmJhY2tncm91" +
        "bmQAAgJpZAANDWxheW91dF93ZWlnaHQACgpwYWRkaW5nVG9wAA0NcGFkZGluZ0JvdHRvbQALC3Bh" +
        "ZGRpbmdMZWZ0AAwMcGFkZGluZ1JpZ2h0AA4Oc2Nyb2xsYmFyU3R5bGUADQ1jbGlwVG9QYWRkaW5n" +
        "ABERZHJhd1NlbGVjdG9yT25Ub3AADg5jYWNoZUNvbG9ySGludAAgIHNjcm9sbGJhckFsd2F5c0Ry" +
        "YXdWZXJ0aWNhbFRyYWNrAAcHYW5kcm9pZAAqKmh0dHA6Ly9zY2hlbWFzLmFuZHJvaWQuY29tL2Fw" +
        "ay9yZXMvYW5kcm9pZAAAAAAbG2FuZHJvaWQud2lkZ2V0LkxpbmVhckxheW91dAAXF2FuZHJvaWQu" +
        "d2lkZ2V0Lkxpc3RWaWV3AIABCABEAAAAxAABAfUAAQH0AAEB1AABAdAAAQGBAQEB1wABAdkAAQHW" +
        "AAEB2AABAX8AAQHrAAEB/AABAQEBAQFpAAEBAAEQABgAAAACAAAA/////w8AAAAQAAAAAgEQAHQA" +
        "AAACAAAA//////////8SAAAAFAAUAAQAAAAAAAAAEAAAAAAAAAD/////CAAAEAEAAAAQAAAAAwAA" +
        "AP////8IAAABDQAGARAAAAACAAAA/////wgAABD/////EAAAAAEAAAD/////CAAAEP////8CARAA" +
        "KAEAAAgAAAD//////////xMAAAAUABQADQAAAAAAAAAQAAAADgAAAP////8IAAAS/////xAAAAAK" +
        "AAAA/////wgAABEAAAACEAAAAAQAAAD/////CAAAAQoAAgEQAAAACAAAAP////8IAAAFARAAABAA" +
        "AAAGAAAA/////wgAAAUBAAAAEAAAAAkAAAD/////CAAABQEQAAAQAAAABwAAAP////8IAAAFAQAA" +
        "ABAAAAALAAAA/////wgAABIAAAAAEAAAAAIAAAD/////CAAAEP////8QAAAAAQAAAP////8IAAAF" +
        "AAAAABAAAAAMAAAA/////wgAABIAAAAAEAAAAA0AAAD/////CAAAAQ0ABgEQAAAABQAAAP////8I" +
        "AAAEAACAPwMBEAAYAAAAFAAAAP//////////EwAAAAMBEAAYAAAAFgAAAP//////////EgAAAAEB" +
        "EAAYAAAAFgAAAP////8PAAAAEAAAAA==";

    /**
     * return XmlPullParser
     * @param xml compiled XML encoded in base64
     * @return XmlPullParser
     */
     public static XmlPullParser getParser(String xml) {
        try {
            byte[] data = Base64.decode(xml, Base64.DEFAULT);

            // XmlBlock block = new XmlBlock(LAYOUT.getBytes("UTF-8"));
            Class<?> clazz = Class.forName("android.content.res.XmlBlock");
            Constructor<?> constructor = clazz.getDeclaredConstructor(byte[].class);
            constructor.setAccessible(true);
            Object block = constructor.newInstance(data);

            // XmlPullParser parser = block.newParser();
            Method method = clazz.getDeclaredMethod("newParser");
            method.setAccessible(true);
            return (XmlPullParser) method.invoke(block);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (java.lang.InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(getParser(LAYOUT), container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (mHavePrefs) {
            bindPreferences();
        }

        mInitDone = true;

        if (savedInstanceState != null) {
            Bundle container = savedInstanceState.getBundle(PREFERENCES_TAG);
            if (container != null) {
                final PreferenceScreen preferenceScreen = getPreferenceScreen();
                if (preferenceScreen != null) {
                    preferenceScreen.restoreHierarchyState(container);
                }
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // FIXME: mPreferenceManager.setOnPreferenceTreeClickListener(this);
        try {
            Class<?> clazz = Class.forName("android.preference.PreferenceManager$OnPreferenceTreeClickListener");
            Object proxy = Proxy.newProxyInstance(clazz.getClassLoader(), new Class[] { clazz }, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if (method.getName().equals("onPreferenceTreeClick")) {
                        return onPreferenceTreeClick((PreferenceScreen) args[0], (Preference) args[1]);
                    }
                    return null;
                }
            });
            callVoidMethod(mPreferenceManager, "setOnPreferenceTreeClickListener", new Class[] { clazz }, new Object[] { proxy });
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        // FIXME: mPreferenceManager.dispatchActivityStop();
        callVoidMethod(mPreferenceManager, "dispatchActivityStop", null, null);
        // FIXME: mPreferenceManager.setOnPreferenceTreeClickListener(null);
        try {
            Class<?> clazz = Class.forName("android.preference.PreferenceManager$OnPreferenceTreeClickListener");
            callVoidMethod(mPreferenceManager, "setOnPreferenceTreeClickListener", new Class[] { clazz }, new Object[] { null });
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroyView() {
        mList = null;
        mHandler.removeCallbacks(mRequestFocus);
        mHandler.removeMessages(MSG_BIND_PREFERENCES);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // FIXME: mPreferenceManager.dispatchActivityDestroy();
        callVoidMethod(mPreferenceManager, "dispatchActivityDestroy", null, null);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen != null) {
            Bundle container = new Bundle();
            preferenceScreen.saveHierarchyState(container);
            outState.putBundle(PREFERENCES_TAG, container);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // FIXME: mPreferenceManager.dispatchActivityResult(requestCode, resultCode, data);
        callVoidMethod(mPreferenceManager, "dispatchActivityResult",
            new Class[] { int.class, int.class, Intent.class },
            new Object[] { requestCode, resultCode, data });
    }

    /**
     * Returns the {@link PreferenceManager} used by this fragment.
     * @return The {@link PreferenceManager}.
     */
    public PreferenceManager getPreferenceManager() {
        return mPreferenceManager;
    }

    /**
     * Sets the root of the preference hierarchy that this fragment is showing.
     *
     * @param preferenceScreen The root {@link PreferenceScreen} of the preference hierarchy.
     */
    public void setPreferenceScreen(PreferenceScreen preferenceScreen) {
        // FIXME: if (mPreferenceManager.setPreferences(preferenceScreen) && preferenceScreen != null) {
        if (callReturnMethod(mPreferenceManager, "setPreferences", Boolean.class,
            new Class[] { PreferenceScreen.class }, new Object[] { preferenceScreen }) && preferenceScreen != null) {
            mHavePrefs = true;
            if (mInitDone) {
                postBindPreferences();
            }
        }
    }

    /**
     * Gets the root of the preference hierarchy that this fragment is showing.
     *
     * @return The {@link PreferenceScreen} that is the root of the preference
     *         hierarchy.
     */
    public PreferenceScreen getPreferenceScreen() {
        // FIXME: return mPreferenceManager.getPreferenceScreen();
        return callReturnMethod(mPreferenceManager, "getPreferenceScreen", PreferenceScreen.class, null, null);
    }

    /**
     * Adds preferences from activities that match the given {@link Intent}.
     *
     * @param intent The {@link Intent} to query activities.
     */
    public void addPreferencesFromIntent(Intent intent) {
        requirePreferenceManager();

        // FIXME: setPreferenceScreen(mPreferenceManager.inflateFromIntent(intent, getPreferenceScreen()));
        setPreferenceScreen(callReturnMethod(mPreferenceManager, "inflateFromIntent", PreferenceScreen.class,
            new Class[] { Intent.class, PreferenceScreen.class },
            new Object[] { intent, getPreferenceScreen() }));
    }

    /**
     * Inflates the given XML resource and adds the preference hierarchy to the current
     * preference hierarchy.
     *
     * @param preferencesResId The XML resource ID to inflate.
     */
    public void addPreferencesFromResource(int preferencesResId) {
        requirePreferenceManager();

        // FIXME: setPreferenceScreen(mPreferenceManager.inflateFromResource(getActivity(),
        //        preferencesResId, getPreferenceScreen()));
        setPreferenceScreen(callReturnMethod(mPreferenceManager, "inflateFromResource", PreferenceScreen.class,
            new Class[] { Context.class, int.class, PreferenceScreen.class },
            new Object[] { getActivity(), preferencesResId, getPreferenceScreen() }));
    }

    /**
     * {@inheritDoc}
     */
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        // FIXME: preference.getFragment()
        if (/*preference.getFragment() != null &&*/
                getActivity() instanceof OnPreferenceStartFragmentCallback) {
            return ((OnPreferenceStartFragmentCallback)getActivity()).onPreferenceStartFragment(
                    this, preference);
        }
        return false;
    }

    /**
     * Finds a {@link Preference} based on its key.
     *
     * @param key The key of the preference to retrieve.
     * @return The {@link Preference} with the key, or null.
     * @see PreferenceGroup#findPreference(CharSequence)
     */
    public Preference findPreference(CharSequence key) {
        if (mPreferenceManager == null) {
            return null;
        }
        return mPreferenceManager.findPreference(key);
    }

    private void requirePreferenceManager() {
        if (mPreferenceManager == null) {
            throw new RuntimeException("This should be called after super.onCreate.");
        }
    }

    private void postBindPreferences() {
        if (mHandler.hasMessages(MSG_BIND_PREFERENCES)) return;
        mHandler.obtainMessage(MSG_BIND_PREFERENCES).sendToTarget();
    }

    private void bindPreferences() {
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen != null) {
            preferenceScreen.bind(getListView());
        }
    }

    /** @hide */
    public ListView getListView() {
        ensureList();
        return mList;
    }

    private void ensureList() {
        if (mList != null) {
            return;
        }
        View root = getView();
        if (root == null) {
            throw new IllegalStateException("Content view not yet created");
        }
        View rawListView = root.findViewById(android.R.id.list);
        if (!(rawListView instanceof ListView)) {
            throw new RuntimeException(
                    "Content has view with id attribute 'android.R.id.list' "
                    + "that is not a ListView class");
        }
        mList = (ListView)rawListView;
        if (mList == null) {
            throw new RuntimeException(
                    "Your content must have a ListView whose id attribute is " +
                    "'android.R.id.list'");
        }
        mList.setOnKeyListener(mListOnKeyListener);
        mHandler.post(mRequestFocus);
    }

    private OnKeyListener mListOnKeyListener = new OnKeyListener() {

        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            Object selectedItem = mList.getSelectedItem();
            if (selectedItem instanceof Preference) {
                View selectedView = mList.getSelectedView();
                // FIXME : return ((Preference)selectedItem).onKey(
                //                selectedView, keyCode, event);
                return callReturnMethod(selectedItem, "onKey", Boolean.class,
                            new Class[] { View.class, int.class, KeyEvent.class },
                            new Object[] { selectedView, keyCode, event });
            }
            return false;
        }

    };

    public static void callVoidMethod(Object receiver, String methodName, Class<?>[] parameterTypes, Object[] args) {
        try {
            Method method = receiver.getClass().getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            method.invoke(receiver, args);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T callReturnMethod(Object receiver, String methodName, Class<T> returnType, Class<?>[] parameterTypes, Object[] args) {
        try {
            Method method = receiver.getClass().getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return (T) method.invoke(receiver, args);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        if (Boolean.class.equals(returnType)) {
            return (T) Boolean.FALSE;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T> T callConstructor(Class<T> returnType, Class<?>[] parameterTypes, Object[] args) {
        try {
            Constructor<?> constructor = returnType.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return (T) constructor.newInstance(args);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (java.lang.InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }
}
