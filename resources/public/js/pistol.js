// stop barcode gun from fucking up everything
$(document).ready(function(){
    $('input').keydown(function(event) {
        if ( event.ctrlKey || event.altKey ) {
            event.preventDefault();
        }
        if ( event.keyCode === 13 && $(this).attr('id') === 'codigo' ) {
            event.preventDefault();
            $(this).blur();
            if ( $(this).parent().hasClass("error-bg") ) {
                $(this).focus();
            }
            else {
                var inputs = $(this).closest('form').find(':input');
                inputs.eq(inputs.index(this)+ 1).focus();
            }
        }
    });
});
