/* ==========================================================================
   /manage/profile
   Live re-validation of the account-number input as the user picks a
   different bank. The catalogue is rendered as <option data-min>,
   <option data-max>, <option data-hint> on the <select>; we mirror
   them into the input's pattern / maxlength / hint text.
   ========================================================================== */
(function () {
    const sel  = document.getElementById('payoutBankName');
    const acc  = document.getElementById('payoutAccountNumber');
    const hint = document.getElementById('accountNumberHint');
    const len  = document.getElementById('accountLengthHint');
    if (!sel || !acc) return;

    function refresh() {
        const opt = sel.options[sel.selectedIndex];
        if (!opt || !opt.dataset.min) {
            acc.pattern = '\\d{8,20}';
            acc.maxLength = 20;
            if (hint) hint.textContent = 'Choose a bank to see the expected length.';
            if (len)  len.textContent  = '';
            return;
        }
        const min = parseInt(opt.dataset.min, 10);
        const max = parseInt(opt.dataset.max, 10);
        acc.pattern = '\\d{' + min + ',' + max + '}';
        acc.maxLength = max;
        const label = (min === max) ? ('Exactly ' + min + ' digits') : (min + '-' + max + ' digits');
        if (len)  len.textContent  = '(' + label + ')';
        if (hint) hint.textContent = opt.dataset.hint || label;
    }

    // Strip non-digits while typing and cap at the chosen bank's max
    acc.addEventListener('input', function () {
        const opt  = sel.options[sel.selectedIndex];
        const max  = opt && opt.dataset.max ? parseInt(opt.dataset.max, 10) : 20;
        const v = (acc.value || '').replace(/\D/g, '');
        acc.value = v.length > max ? v.substring(0, max) : v;
    });

    sel.addEventListener('change', refresh);
    refresh();
})();
