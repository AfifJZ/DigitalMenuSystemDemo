/* ==========================================================================
   /manage/branch/{id}
   Toggles the masked branch password field, and client-side validates
   the new/confirm password match.
   ========================================================================== */
(function () {
    let showingPassword = false;  // starts hidden (masked)
    const inputEl = document.getElementById('maskedBranchPassword');
    const toggleBtn = document.getElementById('toggleMaskBtn');
    const toggleIcon = document.getElementById('toggleIcon');

    if (inputEl && toggleBtn && toggleIcon) {
        // Store the real password in a data attribute
        const realPassword = inputEl.value;
        const hasRealPassword = realPassword && realPassword !== '\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022';

        if (!hasRealPassword) {
            toggleBtn.disabled = true;
        }

        toggleBtn.addEventListener('click', function () {
            if (!hasRealPassword) return;

            if (showingPassword) {
                // Currently showing real password → mask it
                inputEl.value = '\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022';
                toggleIcon.classList.remove('bi-eye-fill');
                toggleIcon.classList.add('bi-eye-slash-fill');
                toggleBtn.title = 'Show password';
                showingPassword = false;
            } else {
                // Currently masked → show real password
                inputEl.value = realPassword;
                toggleIcon.classList.remove('bi-eye-slash-fill');
                toggleIcon.classList.add('bi-eye-fill');
                toggleBtn.title = 'Hide password';
                showingPassword = true;
            }
        });
    }

    const pwForm = document.getElementById('branchPasswordForm');
    if (pwForm) {
        pwForm.addEventListener('submit', function (e) {
            const a = document.getElementById('branchNewPassword').value;
            const b = document.getElementById('branchConfirmPassword').value;
            if (a !== b) {
                e.preventDefault();
                alert('Passwords do not match.');
            }
        });
    }
})();