package android.content;

import java.io.File;

public class Context {
    private File filesDir;

    public Context() {
        this.filesDir = new File("courseflow-test-files");
    }

    public Context(File dir) {
        this.filesDir = dir;
    }

    public File getFilesDir() {
        return filesDir;
    }

    public Context getApplicationContext() {
        return this;
    }

    public android.content.res.Resources getResources() {
        return new android.content.res.Resources();
    }
}
