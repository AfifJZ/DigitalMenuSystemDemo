/* ==========================================================================
   /manage/setup
   Confirms the password and confirm-password match before submit.
   ========================================================================== */
(function () {
    const form = document.querySelector('form[action="/manage/setup"]');
    if (!form) return;
    form.addEventListener('submit', function (e) {
        const password = document.getElementById('branchPassword').value;
        const confirm  = document.getElementById('branchConfirmPassword').value;
        if (password !== confirm) {
            e.preventDefault();
            alert('Passwords do not match. Please try again.');
        }
    });
})();
