/* ==========================================================================
   fragments/admin-branch-bar
   Branch switcher handler. Navigates to /manage/setup when the "+ Add
   branch" option is chosen, and to /manage/branches for "Manage all".
   ========================================================================== */
function handleBranchSelect(sel) {
    if (sel.value === '__add__')        { window.location.href = '/manage/setup';    return; }
    if (sel.value === '__manage__')     { window.location.href = '/manage/branches'; return; }
    document.getElementById('branchSwitchForm').submit();
}
window.handleBranchSelect = handleBranchSelect;
