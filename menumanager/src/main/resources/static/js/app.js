/* ==========================================================================
   Global helpers used across multiple pages.
   Currently: password show/hide toggle and password-match validator
   for the org change-password form on /manage/profile.
   ========================================================================== */
(function () {
    // Password show/hide toggle (used on manage-profile.html)
    window.togglePasswordField = function (fieldId, btn) {
        const input = document.getElementById(fieldId);
        if (!input) return;
        const icon = btn.querySelector('i');
        if (input.type === 'password') {
            input.type = 'text';
            if (icon) {
                icon.classList.remove('bi-eye-slash-fill');
                icon.classList.add('bi-eye-fill');
            }
            btn.title = 'Hide password';
        } else {
            input.type = 'password';
            if (icon) {
                icon.classList.remove('bi-eye-fill');
                icon.classList.add('bi-eye-slash-fill');
            }
            btn.title = 'Show password';
        }
    };

    // Password match validator for change-password form
    const pwForm = document.getElementById('passwordForm');
    if (pwForm) {
        pwForm.addEventListener('submit', function (e) {
            const a = document.getElementById('newPassword').value;
            const b = document.getElementById('confirmPassword').value;
            if (a !== b) {
                e.preventDefault();
                alert('New passwords do not match.');
            }
        });
    }
})();