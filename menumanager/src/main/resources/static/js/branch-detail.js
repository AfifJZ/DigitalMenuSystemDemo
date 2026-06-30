/* ==========================================================================
   /manage/branch/{id}
   Toggles the masked branch password field, with org-password verification
   for on-demand reveal. Also validates the new/confirm password match.
   ========================================================================== */
(function () {
    const inputEl = document.getElementById('maskedBranchPassword');
    const toggleBtn = document.getElementById('toggleMaskBtn');
    const toggleIcon = document.getElementById('toggleIcon');
    const modalEl = document.getElementById('orgPasswordModal');

    var showingPassword = false;
    var pendingReveal = false;

    if (inputEl && toggleBtn && toggleIcon) {
        toggleBtn.addEventListener('click', function () {
            // If a plain password is already loaded in the input, toggle directly
            var currentValue = inputEl.value;
            if (currentValue && currentValue.length > 0) {
                if (showingPassword) {
                    inputEl.type = 'password';
                    toggleIcon.className = 'bi bi-eye-slash-fill';
                    toggleBtn.title = 'Show password';
                    showingPassword = false;
                } else {
                    inputEl.type = 'text';
                    toggleIcon.className = 'bi bi-eye-fill';
                    toggleBtn.title = 'Hide password';
                    showingPassword = true;
                }
                return;
            }

            // No plain password loaded — check if one exists to decrypt
            var hasPassword = inputEl.getAttribute('data-has-password') === 'true';
            if (!hasPassword) {
                showToast('No branch password has been set yet. Use the form below to create one.', 'warning');
                return;
            }

            // Show the org password verification modal
            pendingReveal = true;
            document.getElementById('orgPasswordInput').value = '';
            document.getElementById('orgPasswordError').classList.add('d-none');
            var bsModal = new bootstrap.Modal(modalEl);
            bsModal.show();
        });
    }

    // --- Org password confirmation --------------------------------------------

    var confirmBtn = document.getElementById('orgPasswordConfirmBtn');
    if (confirmBtn && modalEl) {
        confirmBtn.addEventListener('click', function () {
            var orgPassword = document.getElementById('orgPasswordInput').value;
            if (!orgPassword || orgPassword.length < 1) {
                showError('Please enter your organization password.');
                return;
            }

            var branchId = toggleBtn.getAttribute('data-branch-id');
            if (!branchId) {
                showError('Branch ID not found. Please refresh the page.');
                return;
            }

            confirmBtn.disabled = true;
            confirmBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Verifying...';

            fetch('/manage/branch/' + branchId + '/reveal-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: 'orgPassword=' + encodeURIComponent(orgPassword)
            })
            .then(function (r) { return r.json(); })
            .then(function (data) {
                confirmBtn.disabled = false;
                confirmBtn.innerHTML = '<i class="bi bi-check-lg me-1"></i>Verify &amp; reveal';

                if (data.ok) {
                    // Close the modal
                    var bsModal = bootstrap.Modal.getInstance(modalEl);
                    if (bsModal) bsModal.hide();

                    // Populate the input with the decrypted password and show it
                    inputEl.value = data.password;
                    inputEl.type = 'text';
                    toggleIcon.className = 'bi bi-eye-fill';
                    toggleBtn.title = 'Hide password';
                    showingPassword = true;
                } else {
                    showError(data.error || 'Verification failed. Please try again.');
                }
            })
            .catch(function () {
                confirmBtn.disabled = false;
                confirmBtn.innerHTML = '<i class="bi bi-check-lg me-1"></i>Verify &amp; reveal';
                showError('Network error. Please try again.');
            });
        });

        // Clear error when modal is opened and reset on close
        modalEl.addEventListener('hidden.bs.modal', function () {
            document.getElementById('orgPasswordInput').value = '';
            document.getElementById('orgPasswordError').classList.add('d-none');
            pendingReveal = false;
        });
    }

    function showError(msg) {
        var errEl = document.getElementById('orgPasswordError');
        errEl.textContent = msg;
        errEl.classList.remove('d-none');
    }

    // --- Toast notification helper ---

    function showToast(message, type) {
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

    // --- Password match validation ---

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
