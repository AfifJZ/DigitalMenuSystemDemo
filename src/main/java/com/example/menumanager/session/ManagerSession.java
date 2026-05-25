package com.example.menumanager.session;

import java.io.Serializable;

public class ManagerSession implements Serializable {

    public static final String SESSION_KEY = "managerSession";

    private Long organizationId;
    private String organizationName;
    private Long branchId;
    private String branchName;
    private boolean organizationLevel;

    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public boolean isOrganizationLevel() { return organizationLevel; }
    public void setOrganizationLevel(boolean organizationLevel) { this.organizationLevel = organizationLevel; }
}
