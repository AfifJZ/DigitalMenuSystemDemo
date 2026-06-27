/* ==========================================================================
   /staff
   Add/Edit modal helpers: switch the modal between "add" and "edit" mode
   by populating the form fields with the chosen item's data attributes.
   ========================================================================== */
function resetStaffModal() {
    document.querySelector('#addItemModal .modal-title').innerHTML =
        '<i class="bi bi-plus-circle me-2"></i>Add New Item';
    document.querySelector('#addItemModal button[type="submit"]').innerText = 'Save Item';
    document.getElementById('id').value = '';
    document.getElementById('name').value = '';
    document.getElementById('category').value = '';
    document.getElementById('price').value = '';
    document.getElementById('description').value = '';
    document.getElementById('imageUrl').value = '';
}

function populateStaffModal(button) {
    document.querySelector('#addItemModal .modal-title').innerHTML =
        '<i class="bi bi-pencil-square me-2"></i>Edit Item';
    document.querySelector('#addItemModal button[type="submit"]').innerText = 'Update Item';

    document.getElementById('id').value          = button.getAttribute('data-id');
    document.getElementById('name').value        = button.getAttribute('data-name');
    document.getElementById('category').value    = button.getAttribute('data-category');
    document.getElementById('price').value       = parseFloat(button.getAttribute('data-price')).toFixed(2);
    document.getElementById('description').value = button.getAttribute('data-desc');
    document.getElementById('imageUrl').value    = button.getAttribute('data-img');
}

window.resetStaffModal    = resetStaffModal;
window.populateStaffModal = populateStaffModal;
