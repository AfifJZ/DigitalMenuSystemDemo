/* ==========================================================================
   fragments/admin-branch-bar
   Branch switcher handler. Navigates to /manage/setup when the "+ Add
   branch" option is chosen, and to /manage/branches for "Manage all".
   ========================================================================== */
function handleBranchSelect(sel) {
    if (sel.value === '__add__')        { window.location.href = '/manage/setup';    return; }
    if (sel.value === '__manage__')     { window.location.href = '/manage';          return; }
    var form = document.getElementById('branchSwitchForm');
    var redirectInput = document.createElement('input');
    redirectInput.type = 'hidden';
    redirectInput.name = 'redirect';
    redirectInput.value = window.location.pathname + window.location.search;
    form.appendChild(redirectInput);
    form.submit();
}
window.handleBranchSelect = handleBranchSelect;

