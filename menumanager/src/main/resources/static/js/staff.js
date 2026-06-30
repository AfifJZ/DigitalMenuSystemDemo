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
    document.getElementById('imageFile').value = '';
    document.getElementById('imagePreviewContainer').style.display = 'none';
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

function handleImagePreview(input) {
    const previewContainer = document.getElementById('imagePreviewContainer');
    const preview = document.getElementById('imagePreview');
    
    if (input.files && input.files[0]) {
        const reader = new FileReader();
        reader.onload = function(e) {
            preview.src = e.target.result;
            previewContainer.style.display = 'block';
        };
        reader.readAsDataURL(input.files[0]);
    } else {
        previewContainer.style.display = 'none';
    }
}

window.resetStaffModal    = resetStaffModal;
window.populateStaffModal = populateStaffModal;
window.handleImagePreview = handleImagePreview;
