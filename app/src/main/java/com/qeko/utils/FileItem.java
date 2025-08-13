package com.qeko.utils;




import java.io.File;import java.util.List;

public class FileItem {
    private final File file;
    private boolean useThumbnail; // 新增字段
    private final boolean isFolder;
    public long size;
    private boolean isExpanded = false;
    private boolean isLastRead = false;
    private int documentCount = 0;
    private List<FileItem> children;
    private int pageCount;
    private int charsPerPage;
    private boolean pinned;
    private boolean isImage; // 是否图片文件
    public FileItem(File file, boolean isFolder) {
        this.file = file;
        this.isFolder = isFolder;
    }

    public boolean isImage() { return isImage; }
    public void setImage(boolean image) { isImage = image; }
    public File getFile() {
        return file;
    }

    public boolean isFolder() {
        return isFolder;
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    public void setExpanded(boolean expanded) {
        isExpanded = expanded;
    }

    public boolean isLastRead() {
        return isLastRead;
    }

    public void setLastRead(boolean lastRead) {
        isLastRead = lastRead;
    }

    public int getDocumentCount() {
        return documentCount;
    }

    public void setDocumentCount(int documentCount) {
        this.documentCount = documentCount;
    }

    public List<FileItem> getChildren() {
        return children;
    }

    public void setChildren(List<FileItem> children) {
        this.children = children;
    }

    public void setUseThumbnail(boolean useThumbnail) {
        this.useThumbnail = useThumbnail;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setCharsPerPage(int charsPerPage) {
        this.charsPerPage = charsPerPage;
    }

    public int getCharsPerPage() {
        return charsPerPage;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public boolean isPinned() {
        return pinned;
    }
}
