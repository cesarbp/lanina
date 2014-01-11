// Used to check for the existance of barcodes and article names

var jn;
var originalName;
var originalBC;
function validate(id) {
    $(id).removeClass("error");
    if ($(id + "-msg")) {
        $(id + "-msg").remove();
    }
}

function invalidate(id, msg) {
    $(id).addClass("error");
    if ($('#' + id + '-msg').length === 0) {
        $(id + ' .controls').append($('<span id=' + id.replace('#', '') + '-msg>' + msg + '</span>').addClass('help-inline'));
    }
}

function disable_submit() {
    return 0;
}

function no_errors(){
    return $('form').find('.error').length > 0;
}

function enable_submit() {
    return 0;
}

function verify_bc(bc){
    var id = "#codigo";

    if (bc.length > 0 && bc !== "0") {
        $.getJSON('/json/article', {'barcode':bc}, function (article) {
            if ( article && article.codigo && article.codigo !== originalBC ) {
                showError(id, "Ya existe este código");
            } else {
                removeError(id);
            }
        });
    } else if ( bc === "0" ) {
        removeError(id);
    } else if ( bc === "" ) {
        showError(id, "El código no puede estar vacío");
    }
}

function verify_name(name){
    var id = "#nom_art";
    if ( name.length > 0 ) {
        name = name.toUpperCase();
        $.getJSON('/json/article-name', {'name':name}, function (article) {
            if ( article && article.nom_art && article.nom_art !== originalName ) {
                showError(id, "Ya existe este nombre");
            } else {
                removeError(id);
            }
        });
    } else if ( $(".error-bg").length === 0 ) {
        showError(id, "El nombre no puede estar vacío");
    }
}

var res_url = "/json/article/starts_with";

var json = function (first_letter) {
    var res = [];
    $.getJSON(res_url, {'letter': first_letter}, function(results) {
        $.each(results, function(k, v) {
            res.push(v["nom_art"]);
        });
    });
    return res;
};

$(document).ready(function() {
    originalBC = $("#codigo").val();
    originalName = $("#nom_art").val();
    var search_box_id = "#nom_art";
    $.getJSON('/json/all-articles', {}, function(results) {
        jn = results.map(function(o) {
            return o['nom_art'];
        });
        $(search_box_id).typeahead();
        $(search_box_id).data('typeahead').source = jn;
    });
    $('#nom_art').attr('autocomplete', 'off');
    var bc_id = "#codigo-control";
    var na_id = "#nom_art-control";
    $("#codigo").blur(function() {
        verify_bc($("#codigo").val());
    });
    $("#nom_art").blur(function() {
        verify_name($("#nom_art").val());
    });

    var trie = {};


    $(search_box_id).on("keyup change", function () {

    });

});
