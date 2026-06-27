/* ==========================================================================
   /manage/login  +  /manage/register
   Client-side validation: make sure password and confirm-password match.
   ========================================================================== */
(function () {
    const form = document.getElementById('registerForm');
    if (!form) return;
    form.addEventListener('submit', function (e) {
        const p = document.getElementById('regPassword').value;
        const c = document.getElementById('regConfirmPassword').value;
        if (p !== c) {
            e.preventDefault();
            alert('Passwords do not match. Please try again.');
        }
    });
})();
