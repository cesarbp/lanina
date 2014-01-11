$(document).ready(function() {
    $.getJSON("/clientes/todos/", function(results) {
        $("#nombre").typeahead({
            name: 'clientes',
            source: results
        });
    });

    $("#nombre").change(function() {
        $.getJSON("/clientes/", {"nombre": $("#nombre").val()}, function(result) {
            a = result;
            if ( result ) {
                $("#calle").val(result.CALLE);
                $("#colonia").val(result.COLONIA);
                $("#municipio").val(result.MUNICIPIO);
                $("#estado").val(result.ESTADO);
                $("#numero-ext").val(result['NUMERO-EXT']);
                $("#cp").val(result.CP);
                $("#correo").val(result.CORREO);
                $("#rfc").val(result.RFC);
                $("#aprob").focus();
            }
        });
    });
    $("#nombre").focus();
});
