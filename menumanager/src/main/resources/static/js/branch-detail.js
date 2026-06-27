/* ==========================================================================
   /manage/branch/{id}
   Toggles the masked branch password field, and client-side validates
   the new/confirm password match.
   ========================================================================== */
(function () {
    const inputEl = document.getElementById('maskedBranchPassword');
    const toggleBtn = document.getElementById('toggleMaskBtn');
    const toggleIcon = document.getElementById('toggleIcon');

    var showingPassword = false;

    if (inputEl && toggleBtn && toggleIcon) {
        // Check if the input currently has a real password value
        var realPassword = inputEl.value;
        var hasRealPassword = realPassword && realPassword.length > 0;

        toggleBtn.addEventListener('click', function () {
            // Re-check the value on each click (in case it changed)
            realPassword = inputEl.value;
            hasRealPassword = realPassword && realPassword.length > 0;

            if (!hasRealPassword) {
                // No password to reveal — show feedback
                showToast('Password is securely hashed — cannot be revealed. Use the form below to set a new one.', 'warning');
                return;
            }

            if (showingPassword) {
                // Currently showing real password → mask it
                inputEl.type = 'password';
                toggleIcon.className = 'bi bi-eye-slash-fill';
                toggleBtn.title = 'Show password';
                showingPassword = false;
            } else {
                // Currently masked → show real password
                inputEl.type = 'text';
                toggleIcon.className = 'bi bi-eye-fill';
                toggleBtn.title = 'Hide password';
                showingPassword = true;
            }
        });

        // Set initial icon based on whether password is available
        if (!hasRealPassword) {
            toggleIcon.className = 'bi bi-lock-fill';
            toggleBtn.title = 'Click for info — password cannot be revealed';
        }
    }

    // Toast notification helper
    function showToast(message, type) {
        // Remove existing toast if any
        var old = document.getElementById('dynamicToast');
        if (old) old.remove();

        var toastEl = document.createElement('div');
        toastEl.id = 'dynamicToast';
        toastEl.className = 'toast align-items-center text-bg-' + (type || 'warning') + ' border-0 position-fixed bottom-0 end-0 m-3';
        toastEl.setAttribute('role', 'alert');
        toastEl.setAttribute('aria-live', 'polite');
        toastEl.setAttribute('aria-atomic', 'true');
        toastEl.style.zIndex = '9999';
        toastEl.innerHTML = '<div class="d-flex"><div class="toast-body">' + message + '</div><button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button></div>';
        document.body.appendChild(toastEl);

        var bsToast = new bootstrap.Toast(toastEl, { delay: 4000 });
        bsToast.show();
    }

    // Password match validation
    var pwForm = document.getElementById('branchPasswordForm');
    if (pwForm) {
        pwForm.addEventListener('submit', function (e) {
            var a = document.getElementById('branchNewPassword').value;
            var b = document.getElementById('branchConfirmPassword').value;
            if (a !== b) {
                e.preventDefault();
                showToast('Passwords do not match.', 'danger');
            }
        });
    }
})();
