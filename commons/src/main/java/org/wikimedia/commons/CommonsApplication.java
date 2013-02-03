package org.wikimedia.commons;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;

import javax.xml.transform.*;

import android.accounts.*;
import android.app.Application;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.download.ImageDownloader;
import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.mediawiki.api.*;
import org.w3c.dom.Node;
import org.wikimedia.commons.auth.WikiAccountAuthenticator;
import org.apache.http.HttpVersion;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;
import org.wikimedia.commons.data.DBOpenHelper;

// TODO: Use ProGuard to rip out reporting when publishing
@ReportsCrashes(formKey = "",
        mailTo = "yuvipanda@wikimedia.org",
        mode = ReportingInteractionMode.DIALOG,
        resDialogText = R.string.crash_dialog_text,
        resDialogTitle = R.string.crash_dialog_title,
        resDialogCommentPrompt = R.string.crash_dialog_comment_prompt,
        resDialogOkToast = R.string.crash_dialog_ok_toast
)
public class CommonsApplication extends Application {

    private DBOpenHelper dbOpenHelper;

    private MWApi api;
    private Account currentAccount = null; // Unlike a savings account...
    public static final String API_URL = "http://test.wikipedia.org/w/api.php";
   
    public static MWApi createMWApi() {
        DefaultHttpClient client = new DefaultHttpClient();
        return new MWApi(API_URL, client);
    }

    public DBOpenHelper getDbOpenHelper() {
        if(dbOpenHelper == null) {
            dbOpenHelper = new DBOpenHelper(this);
        }
        return dbOpenHelper;
    }

    public class ContentUriImageDownloader extends ImageDownloader {

        @Override
        protected InputStream getStreamFromNetwork(URI uri) throws IOException {
            return super.getStream(uri); // Pass back to parent code, which handles http, https, etc
        }

        @Override
        protected InputStream getStreamFromOtherSource(URI imageUri) throws IOException {
            if(imageUri.getScheme().equals("content")) {
                return getContentResolver().openInputStream(Uri.parse(imageUri.toString()));
            }
            throw new RuntimeException("Not a content URI: " + imageUri);
        }
    }

    @Override
    public void onCreate() {
        ACRA.init(this);
        super.onCreate();
        // Fire progress callbacks for every 3% of uploaded content
        System.setProperty("in.yuvi.http.fluent.PROGRESS_TRIGGER_THRESHOLD", "3.0");
        api = createMWApi();


        ImageLoaderConfiguration imageLoaderConfiguration = new ImageLoaderConfiguration.Builder(getApplicationContext())
                .imageDownloader(new ContentUriImageDownloader()).build();
        ImageLoader.getInstance().init(imageLoaderConfiguration);
    }
    
    public MWApi getApi() {
        return api;
    }
    
    public Account getCurrentAccount() {
        if(currentAccount == null) {
            AccountManager accountManager = AccountManager.get(this);
            Account[] allAccounts = accountManager.getAccountsByType(WikiAccountAuthenticator.COMMONS_ACCOUNT_TYPE);
            if(allAccounts.length != 0) {
                currentAccount = allAccounts[0];
            }
        }
        return currentAccount;
    }
    
    public Boolean revalidateAuthToken() {
        AccountManager accountManager = AccountManager.get(this);
        Account curAccount = getCurrentAccount();
       
        if(curAccount == null) {
            return false; // This should never happen
        }
        
        accountManager.invalidateAuthToken(WikiAccountAuthenticator.COMMONS_ACCOUNT_TYPE, api.getAuthCookie());
        try {
            String authCookie = accountManager.blockingGetAuthToken(curAccount, "", false);
            api.setAuthCookie(authCookie);
            return true;
        } catch (OperationCanceledException e) {
            e.printStackTrace();
            return false;
        } catch (AuthenticatorException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String getStringFromDOM(Node dom) {
       javax.xml.transform.Transformer transformer = null;
       try {
           transformer = TransformerFactory.newInstance().newTransformer();
       } catch (TransformerConfigurationException e) {
           // TODO Auto-generated catch block
           e.printStackTrace();
       } catch (TransformerFactoryConfigurationError e) {
           // TODO Auto-generated catch block
           e.printStackTrace();
       }

       StringWriter  outputStream = new StringWriter();
       javax.xml.transform.dom.DOMSource domSource = new javax.xml.transform.dom.DOMSource(dom);
       javax.xml.transform.stream.StreamResult strResult = new javax.xml.transform.stream.StreamResult(outputStream);

       try {
        transformer.transform(domSource, strResult);
       } catch (TransformerException e) {
           // TODO Auto-generated catch block
           e.printStackTrace();
       } 
       return outputStream.toString();
    }
    
    static public <T> void executeAsyncTask(AsyncTask<T, ?, ?> task,
            T... params) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
        }
        else {
            task.execute(params);
        }
    } 
}