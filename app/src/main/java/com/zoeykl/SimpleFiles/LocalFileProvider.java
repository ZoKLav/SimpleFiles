package com.zoeykl.SimpleFiles;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileNotFoundException;

/** Read-only content:// bridge for sharing files without exposing file:// URIs. */
public class LocalFileProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private File file(Uri uri){String path=uri.getLastPathSegment();return path==null?null:new File(path);}
    @Override public String getType(Uri uri){File f=file(uri);if(f==null)return "application/octet-stream";String n=f.getName();int i=n.lastIndexOf('.');String m=i<0?null:MimeTypeMap.getSingleton().getMimeTypeFromExtension(n.substring(i+1).toLowerCase());return m==null?"application/octet-stream":m;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException("Read only");File f=file(uri);if(f==null||!f.isFile())throw new FileNotFoundException();return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){File f=file(uri);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor c=new MatrixCursor(cols);if(f!=null&&f.exists()){Object[] row=new Object[cols.length];for(int i=0;i<cols.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))row[i]=f.getName();else if(OpenableColumns.SIZE.equals(cols[i]))row[i]=f.length();}c.addRow(row);}return c;}
    @Override public Uri insert(Uri uri,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
