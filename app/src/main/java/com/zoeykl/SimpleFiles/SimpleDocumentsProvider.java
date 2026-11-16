package com.zoeykl.SimpleFiles;

// Developed by ZoeyKL

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

/**
 * Read-only Storage Access Framework provider for shared internal storage. The system DocumentsUI
 * can display this root, and files returned from our own picker remain usable through content://
 * URIs (including persisted grants for ACTION_OPEN_DOCUMENT). Root-only locations are deliberately
 * not exposed.
 */
public final class SimpleDocumentsProvider extends DocumentsProvider {
  private static final String ROOT_ID = "primary";
  private static final String DOCUMENT_PREFIX = ROOT_ID + ":";
  private static final String[] ROOT_COLUMNS = {
    DocumentsContract.Root.COLUMN_ROOT_ID,
    DocumentsContract.Root.COLUMN_DOCUMENT_ID,
    DocumentsContract.Root.COLUMN_TITLE,
    DocumentsContract.Root.COLUMN_SUMMARY,
    DocumentsContract.Root.COLUMN_FLAGS,
    DocumentsContract.Root.COLUMN_MIME_TYPES,
    DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
  };
  private static final String[] DOCUMENT_COLUMNS = {
    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
    DocumentsContract.Document.COLUMN_MIME_TYPE,
    DocumentsContract.Document.COLUMN_SIZE,
    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    DocumentsContract.Document.COLUMN_FLAGS
  };

  @Override
  public boolean onCreate() {
    return true;
  }

  private static File storageRoot() throws FileNotFoundException {
    File root = Environment.getExternalStorageDirectory();
    if (root == null) throw new FileNotFoundException("Shared storage unavailable");
    try {
      return root.getCanonicalFile();
    } catch (IOException e) {
      throw new FileNotFoundException(e.getMessage());
    }
  }

  /** Keep every document inside the shared-storage root, even through symlinks. */
  private static File resolve(String documentId) throws FileNotFoundException {
    if (documentId == null || !documentId.startsWith(DOCUMENT_PREFIX))
      throw new FileNotFoundException("Invalid document ID");
    String relative = documentId.substring(DOCUMENT_PREFIX.length());
    if (relative.startsWith("/") || relative.indexOf('\\') >= 0 || relative.indexOf('\0') >= 0)
      throw new FileNotFoundException("Invalid document path");
    File root = storageRoot();
    try {
      File file = new File(root, relative).getCanonicalFile();
      if (!file.equals(root) && !file.getPath().startsWith(root.getPath() + File.separator))
        throw new FileNotFoundException("Outside shared storage");
      return file;
    } catch (IOException e) {
      throw new FileNotFoundException(e.getMessage());
    }
  }

  public static Uri uriForFile(Context context, File file) throws IOException {
    File root = storageRoot();
    File canonical = file.getCanonicalFile();
    if (!canonical.isFile()
        || !canonical.canRead()
        || !canonical.getPath().startsWith(root.getPath() + File.separator))
      throw new FileNotFoundException("Not a readable shared-storage file");
    String relative = canonical.getPath().substring(root.getPath().length() + 1);
    return DocumentsContract.buildDocumentUri(
        context.getPackageName() + ".documents", DOCUMENT_PREFIX + relative);
  }

  private static String documentId(File file) throws FileNotFoundException {
    File root = storageRoot();
    try {
      File canonical = file.getCanonicalFile();
      if (canonical.equals(root)) return DOCUMENT_PREFIX;
      if (!canonical.getPath().startsWith(root.getPath() + File.separator))
        throw new FileNotFoundException("Outside shared storage");
      return DOCUMENT_PREFIX + canonical.getPath().substring(root.getPath().length() + 1);
    } catch (IOException e) {
      throw new FileNotFoundException(e.getMessage());
    }
  }

  private static String mimeType(File file) {
    if (file.isDirectory()) return DocumentsContract.Document.MIME_TYPE_DIR;
    String name = file.getName();
    int dot = name.lastIndexOf('.');
    if (dot < 0) return "application/octet-stream";
    String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
    if ("jpg".equals(extension)) extension = "jpeg";
    String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    return mime == null ? "application/octet-stream" : mime;
  }

  @Override
  public Cursor queryRoots(String[] projection) throws FileNotFoundException {
    MatrixCursor result = new MatrixCursor(projection == null ? ROOT_COLUMNS : projection);
    File root = storageRoot();
    MatrixCursor.RowBuilder row = result.newRow();
    for (String column : result.getColumnNames()) {
      switch (column) {
        case DocumentsContract.Root.COLUMN_ROOT_ID:
          row.add(ROOT_ID);
          break;
        case DocumentsContract.Root.COLUMN_DOCUMENT_ID:
          row.add(DOCUMENT_PREFIX);
          break;
        case DocumentsContract.Root.COLUMN_TITLE:
          row.add("SimpleFiles");
          break;
        case DocumentsContract.Root.COLUMN_SUMMARY:
          row.add("Internal storage");
          break;
        case DocumentsContract.Root.COLUMN_FLAGS:
          row.add(0);
          break;
        case DocumentsContract.Root.COLUMN_MIME_TYPES:
          row.add("*/*");
          break;
        case DocumentsContract.Root.COLUMN_AVAILABLE_BYTES:
          row.add(root.getUsableSpace());
          break;
        default:
          row.add(null);
      }
    }
    return result;
  }

  private void addDocument(MatrixCursor cursor, File file) throws FileNotFoundException {
    MatrixCursor.RowBuilder row = cursor.newRow();
    for (String column : cursor.getColumnNames()) {
      switch (column) {
        case DocumentsContract.Document.COLUMN_DOCUMENT_ID:
          row.add(documentId(file));
          break;
        case DocumentsContract.Document.COLUMN_DISPLAY_NAME:
          row.add(file.getName().isEmpty() ? "Internal storage" : file.getName());
          break;
        case DocumentsContract.Document.COLUMN_MIME_TYPE:
          row.add(mimeType(file));
          break;
        case DocumentsContract.Document.COLUMN_SIZE:
          row.add(file.isFile() ? file.length() : null);
          break;
        case DocumentsContract.Document.COLUMN_LAST_MODIFIED:
          row.add(file.lastModified());
          break;
        case DocumentsContract.Document.COLUMN_FLAGS:
          row.add(0);
          break;
        default:
          row.add(null);
      }
    }
  }

  @Override
  public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
    File file = resolve(documentId);
    if (!file.exists()) throw new FileNotFoundException(documentId);
    MatrixCursor result = new MatrixCursor(projection == null ? DOCUMENT_COLUMNS : projection);
    addDocument(result, file);
    return result;
  }

  @Override
  public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder)
      throws FileNotFoundException {
    File parent = resolve(parentDocumentId);
    if (!parent.isDirectory() || !parent.canRead())
      throw new FileNotFoundException("Folder unavailable");
    File[] children = parent.listFiles();
    if (children == null) throw new FileNotFoundException("Folder cannot be listed");
    MatrixCursor result = new MatrixCursor(projection == null ? DOCUMENT_COLUMNS : projection);
    for (File child : children) {
      if (!child.canRead()) continue;
      try {
        addDocument(result, child);
      } catch (FileNotFoundException ignored) {
        /* Outside root (symlink). */
      }
    }
    return result;
  }

  @Override
  public boolean isChildDocument(String parentDocumentId, String documentId) {
    try {
      File parent = resolve(parentDocumentId);
      File child = resolve(documentId);
      return !child.equals(parent) && child.getPath().startsWith(parent.getPath() + File.separator);
    } catch (FileNotFoundException e) {
      return false;
    }
  }

  @Override
  public ParcelFileDescriptor openDocument(
      String documentId, String mode, CancellationSignal signal) throws FileNotFoundException {
    if (!"r".equals(mode)) throw new FileNotFoundException("Read-only document provider");
    File file = resolve(documentId);
    if (!file.isFile() || !file.canRead()) throw new FileNotFoundException(documentId);
    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
  }
}
