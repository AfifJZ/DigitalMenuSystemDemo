/* ==========================================================================
   /manage/forgot-password
   Toggles the "branch name" field based on the reset-type selector, and
   client-side validates the password match on the reset step.
   ========================================================================== */
(function () {
    const sel = document.getElementById('resetType');
    if (sel) {
        function updateForgotFields() {
            const isBranch = sel.value === 'branch';
            const branchField = document.getElementById('branchNameField');
            const orgName = document.getElementById('organizationName');
            const branchName = document.getElementById('branchName');
            if (branchField) branchField.classList.toggle('d-none', !isBranch);
            if (orgName) orgName.required = true;
            if (branchName) branchName.required = isBranch;
        }
        sel.addEventListener('change', updateForgotFields);
        updateForgotFields();
    }

    const resetForm = document.querySelector('form[action="/manage/reset-password"]');
    if (resetForm) {
        resetForm.addEventListener('submit', function (e) {
            const newPass = document.getElementById('newPassword').value;
            const confirmPass = document.getElementById('confirmPassword').value;
            if (newPass !== confirmPass) {
                e.preventDefault();
                alert('Passwords do not match.');
            }
        });
    }

    // Expose for the inline onchange="..." attribute in the template
    window.updateForgotFields = updateForgotFields;
})();
