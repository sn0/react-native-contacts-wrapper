package com.lynxit.contactswrapper;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.net.URI;
import java.util.*;

import com.facebook.react.*;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.JavaScriptModule;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ViewManager;

public class ContactsWrapper extends ReactContextBaseJavaModule implements ActivityEventListener {

    private static final int CONTACT_REQUEST = 1;
    private static final int EMAIL_REQUEST = 2;
    private static final int PERMISSIONS_REQUEST_READ_CONTACTS = 3;
    public static final String E_CONTACT_CANCELLED = "E_CONTACT_CANCELLED";
    public static final String E_CONTACT_NO_DATA = "E_CONTACT_NO_DATA";
    public static final String E_CONTACT_NO_EMAIL = "E_CONTACT_NO_EMAIL";
    public static final String E_CONTACT_EXCEPTION = "E_CONTACT_EXCEPTION";
    public static final String E_CONTACT_PERMISSION = "E_CONTACT_PERMISSION";
    private Promise mContactsPromise;
    private int mRequestCode = -1;
    private Activity mCtx;
    private final ContentResolver contentResolver;
    private static final List<String> JUST_ME_PROJECTION = new ArrayList<String>() {{
        add(ContactsContract.Contacts.Data.MIMETYPE);
        add(ContactsContract.Profile.DISPLAY_NAME);
        add(ContactsContract.CommonDataKinds.Contactables.PHOTO_URI);
        add(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);
        add(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);
        add(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME);
        add(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME);
        add(ContactsContract.CommonDataKinds.Phone.NUMBER);
        add(ContactsContract.CommonDataKinds.Phone.TYPE);
        add(ContactsContract.CommonDataKinds.Phone.LABEL);
        add(ContactsContract.CommonDataKinds.Email.DATA);
        add(ContactsContract.CommonDataKinds.Email.ADDRESS);
        add(ContactsContract.CommonDataKinds.Email.TYPE);
        add(ContactsContract.CommonDataKinds.Email.LABEL);
    }};


    public ContactsWrapper(ReactApplicationContext reactContext) {
        super(reactContext);
        this.contentResolver = getReactApplicationContext().getContentResolver();
        reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "ContactsWrapper";
    }



    @ReactMethod
    public void getContact(Promise contactsPromise) {
        launchPicker(contactsPromise, CONTACT_REQUEST);
    }

    @ReactMethod
    public void getEmail(Promise contactsPromise) {
        launchPicker(contactsPromise, EMAIL_REQUEST);
    }

    /**
     * Lanch the contact picker, with the specified requestCode for returned data.
     * @param contactsPromise - promise passed in from React Native.
     * @param requestCode - request code to specify what contact data to return
     */
    private void launchPicker(Promise contactsPromise, int requestCode) {
        mContactsPromise = contactsPromise;
        mRequestCode = requestCode;

        if (ContextCompat.checkSelfPermission(this.getReactApplicationContext(),
                Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this.getCurrentActivity(),
                    new String[]{Manifest.permission.READ_CONTACTS},
                    PERMISSIONS_REQUEST_READ_CONTACTS);
        } else {
            requestContact();
        }
    }

    public void requestContact() {
        Cursor cursor = this.contentResolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
        if (cursor != null && mContactsPromise != null) {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
            mCtx = getCurrentActivity();
            if (intent.resolveActivity(mCtx.getPackageManager()) != null) {
                mCtx.startActivityForResult(intent, mRequestCode);
            }
            cursor.close();
        } else {
            mContactsPromise.reject(E_CONTACT_PERMISSION, "no permission");
        }
    }

    public static void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_READ_CONTACTS: {
                if (grantResults.length > 0  && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // TODO: re-request permission
                }
                return;
            }
        }
    }

    @Override
    public void onActivityResult(Activity ContactsWrapper, final int requestCode, final int resultCode, final Intent intent) {

        if(mContactsPromise == null || mCtx == null || (requestCode != CONTACT_REQUEST && requestCode != EMAIL_REQUEST)){
          return;
        }

        String email = null;
        switch (resultCode) {
            case (Activity.RESULT_OK):
                Uri contactUri = intent.getData();
                switch(requestCode) {
                    case(CONTACT_REQUEST):
                        try {
                            /* Retrieve all possible data about contact and return as a JS object */

                            //First get ID
                            String id = null;
                            int idx;
                            WritableMap contactData = Arguments.createMap();
                            Cursor cursor = this.contentResolver.query(contactUri, null, null, null, null);
                            if (cursor != null && cursor.moveToFirst()) {
                                idx = cursor.getColumnIndex(ContactsContract.Contacts._ID);
                                id = cursor.getString(idx);
                            } else {
                                mContactsPromise.reject(E_CONTACT_NO_DATA, "Contact Data Not Found");
                                return;
                            }


                            // Build the Entity URI.
                            Uri.Builder b = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id).buildUpon();
                            b.appendPath(ContactsContract.Contacts.Entity.CONTENT_DIRECTORY);
                            contactUri = b.build();

                            // Create the projection (SQL fields) and sort order.
                            String[] projection = {
                                ContactsContract.Contacts.Entity.MIMETYPE,
                                ContactsContract.Contacts.Entity.DATA1
                            };
                            String sortOrder = ContactsContract.Contacts.Entity.RAW_CONTACT_ID + " ASC";
                            cursor = this.contentResolver.query(contactUri, projection, null, null, sortOrder);
                            if(cursor == null)  return;

                            String mime;
                            boolean foundData = false;
                            /* Map Any contact data we want returned to the JS object key for React Native */
                            HashMap<String, String> returnKeys = new HashMap<String, String>();
                            returnKeys.put(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, "name");
                            returnKeys.put(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, "phone");
                            returnKeys.put(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, "email");
                            returnKeys.put(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE, "postal");

                            int dataIdx = cursor.getColumnIndex(ContactsContract.Contacts.Entity.DATA1);
                            int mimeIdx = cursor.getColumnIndex(ContactsContract.Contacts.Entity.MIMETYPE);
                            if (cursor.moveToFirst()) {
                                do {
                                    mime = cursor.getString(mimeIdx);
                                    if(returnKeys.containsKey(mime)) {
                                        if (returnKeys.get(mime) == "postal") {
                                            String[] address = cursor.getString(dataIdx).split("\\n");
                                            if (address.length>0) {
                                                contactData.putString("street", address[0]);
                                            }
                                            if (address.length>1) {
                                                String[] postal = address[1].split(" ");
                                                if (postal.length>0) {
                                                    contactData.putString("postalCode", postal[0]);
                                                }
                                                if (postal.length>1) {
                                                    contactData.putString("city", postal[1]);
                                                }
                                            }
                                        } else {
                                            contactData.putString((String) returnKeys.get(mime), cursor.getString(dataIdx));
                                        }
                                        foundData = true;
                                    }
                                } while (cursor.moveToNext());
                            }

                            cursor.close();
                            if(foundData) {
                                mContactsPromise.resolve(contactData);
                                return;
                            } else {
                                mContactsPromise.reject(E_CONTACT_NO_DATA, "No data found for contact");
                                return;
                            }
                        } catch (Exception e) {
                            mContactsPromise.reject(E_CONTACT_EXCEPTION, e.getMessage());
                            return;
                        }
                        /* No need to break as all paths return */
                    case(EMAIL_REQUEST):
                        /* Return contacts first email address, as string */
                        try {


                            // get the contact id from the Uri
                            String id = contactUri.getLastPathSegment();

                            // query for everything email
                            Cursor cursor = mCtx.getContentResolver().query(ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                                                                            null, ContactsContract.CommonDataKinds.Email.CONTACT_ID + "=?", new String[]{id},
                                                                            null);

                            int emailIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA);

                            // For now, return only the first email address, as a string
                            if (cursor.moveToFirst()) {
                                email = cursor.getString(emailIdx);
                                mContactsPromise.resolve(email);
                                return;
                            } else {
                                //Contact has no email address stored
                                mContactsPromise.reject(E_CONTACT_NO_EMAIL, "No email found for contact");
                                return;
                            }
                        } catch (Exception e) {
                            mContactsPromise.reject(E_CONTACT_EXCEPTION, e.getMessage());
                            return;
                        }
                        /* No need to break as all paths return */
                    default:
                        //Unexpected return code - shouldn't happen, but catch just in case
                        mContactsPromise.reject(E_CONTACT_EXCEPTION, "Unexpected error in request");
                        return;
                }
            default:
                //Request was cancelled
                mContactsPromise.reject(E_CONTACT_CANCELLED, "Cancelled");
                return;
        }
    }

    @Override
    public void onNewIntent(Intent intent) {

    }
}
