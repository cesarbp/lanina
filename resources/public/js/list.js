// Shopping list
var art_names;
function toArray(obj) {
    var array = [];
    // iterate backwards ensuring that length is an UInt32
    for (var i = obj.length >>> 0; i--;) {
        array[i] = obj[i];
    }
    return array;
}

function isInt(s) {
    for (var i = 0; i < s.length; i++) {
        var parsed = parseInt(s[i]);
        if (parsed !== 0 && !parsed)
            return false;
    }
    return true;
}

function readFile(url) {
    var html = "";
    $.ajax({
        url: url,
        data: {},
        async: false,
        success: function(response) {
            html = response;
        }
    });
    return html;
}

function restore_progress() {
    $.getJSON('/backup/list/get/', {}, function(h) {
        if (h !== "") {
            $('#main').html(h);
        } else {
            alert('No hay ninguna lista guardada.');
        }
    });
    return false;
}

function save_progress() {
    $.getJSON('/backup/list/', {'html': $('#main').html()}, function (r) {
        if (r === "success") {
            alert("Se ha guardado la lista.");
        }
    });
    return false;
}

function article_row(article, quantity) {
    var id = article._id;
    var price = article.costo_caja;

    var name = article.nom_art;

    var total = price * parseInt(quantity);

    var html = '<tr class="article-row" id=' + id + '><td class="art_name">' +
        name.toString() + '<td class="art_quantity">' +
        quantity.toString() + '</td><td class="art_price">' +
        price.toFixed(2).toString() + '</td>' + '</td><td class="art_total">' +
        total.toFixed(2).toString() + '</td>' +
        '<td><a class="btn" onclick="add_article_row(\'' + id + '\',1);"><i class="icon-chevron-up"></i></a>' +
        '<a class="btn" onclick="add_article_row(\'' + id + '\',-1);"><i class="icon-chevron-down"></i></a></td>' +
        '<td><a class="btn btn-danger" onclick="remove_article_row(\'' + id + '\');"><i class="icon-remove"></i></a></td></tr>';
    return html;
}

function calculate_total() {
    var articles = $("#articles-table").children().children().slice(1);
    articles = toArray(articles);
    if (articles.length === 0) {
        return 0;
    }
    var totals = articles.map(function (r) {
        return parseFloat(r.children[3].innerHTML);
    });

    var total = totals.reduce(function (a,b) {return a + b;});
    return total;
}

function update_total() {
    setTimeout(function() {
        var total = calculate_total().toFixed(2);
        $("#total").text('Total: ' + total.toString());
    }, 200);
}

// actually bd id not barcode
function ticket_links(barcode, quantity, increase) {
    var ticket_link = $("#ticket");
    barcode = barcode.replace(/\s/g, '_');
    var req_html =  barcode + "=" + quantity;
    var prev_quant = increase ? quantity - 1 : quantity + 1;
    var prev_html = barcode + "=" + prev_quant.toString();
    var ticket_href = '/listas/compras/nuevo/';

    if ( ticket_link.length !== 0 )
    {
        ticket_link_href = ticket_link[0].href;
        if (ticket_link_href.search("\\?" + prev_html) >= 0 || ticket_link_href.search("&" + prev_html) >= 0){
            $("#ticket")[0].href = $("#ticket")[0].href.replace("?" + prev_html, "?" + req_html);
            $("#ticket")[0].href = $("#ticket")[0].href.replace("&" + prev_html, "&" + req_html);
         } else {
            $("#ticket")[0].href = ticket_link_href + "&" + req_html;
         }
    } else {
         var ticket_html = '<a id="ticket" on-click="draw_modal();" class="btn btn-primary" href="/listas/compras/nuevo/?' +
                 req_html + '">F9 - Generar Compra</a>';
        var form_html = '<div id="gen-tickets" class="form-actions">' + ticket_html + '</div>';
        $("#main").append(form_html);
    }
    return 0;
}

function remove_ticket_links(barcode) {
    var ticket_link = $("#ticket")[0];

    if ( ticket_link )
    {
        var re = new RegExp ('[?|&]' + barcode + '=\\d+');
        ticket_link.href = ticket_link.href.replace(re, '');

        if (ticket_link.href.search(/&/) === -1)
            $('#gen-tickets').remove();
    }
}

function remove_article_row(barcode) {
    var id_barcode = "#" + decodeURIComponent(barcode);
    remove_ticket_links(barcode);
    if ($(id_barcode)) {
        $(id_barcode).remove();
        remove_ticket_links(barcode);
        update_total();
    }
}
// So damn ugly
function add_article_row(denom, n) {
    var quantity;
    var price;
    var new_quantity;
    var article;

    if ( denom.length > 0 )
        $.ajax({url: '/json/article/id', dataType: 'json', data: {id: denom}, async: false, success: function(art) {
            if ( art && art._id )
                article = art;
            else
                $.ajax({url: '/json/article', dataType: 'json', data: {barcode: denom}, async: false, success: function(art1) {
                    if ( art1 && art1._id )
                        article = art1;
                    else
                        $.ajax({url: '/json/article-name', dataType: 'json', data: {name: denom}, async: false, success: function(art2) {
                            if ( art2 && art2._id )
                                article = art2;
                            else
                                alert("Artículo no encontrado.");
                        }});

                }});
        }});

    if ( !article )
        return false;
    var id = article._id;
    html_id = "#" + id;
    if ( $(html_id).length > 0 )
    {
        quantity = $(html_id).children()[1].innerHTML;
        price = $(html_id).children()[2].innerHTML;
        new_quantity = n + parseInt(quantity);
        if ( 0 >= new_quantity )
        {
            remove_article_row(id);
            return 0;
        }
        var total = parseFloat(price) * new_quantity;

        // Modify the html
        $(html_id).children()[1].innerHTML = new_quantity.toString();
        $(html_id).children()[3].innerHTML = total.toFixed(2).toString();
        if ( $(html_id).length !== 0 )
                ticket_links(id, new_quantity, (n > 0));
    }
    else
    {
        $("#articles-table").append(article_row(article, n));
        ticket_links(id, n, true);
    }
    $("#barcode-field").val("");
    update_total();
    return 0;
}

function barcode_listener (field, e) {
    var code = e.keyCode || e.which;
    if ( code == 13 )
    {
        var barcode = $("#barcode-field").val();
        var quantity = $("#quantity-field").val() || 1;
        $("#quantity-field").val("");
        if (isInt(quantity)) {
            quantity = parseInt(quantity);
        }
        else {
            quantity = 1;
        }
        add_article_row(barcode, quantity);
        return false;
    }
    else
        return true;
}

function article_listener (field, e) {
    var code = e.keyCode || e.which;
    if (code == 13 && $("#article-field").val().length > 0) {
        var name = $("#article-field").val();
        var quantity = $("#quantity-field").val() || 1;
        $("#quantity-field").val("");
        $("#article-field").val("");
        if ( isInt(quantity) )
            quantity = parseInt(quantity);
        else
            quantity = 1;
        add_article_row(name, quantity);
        return false;
    }
}

function quantity_listener (field, e) {
    var code = e.keyCode || e.which;
    if (code == 13 && $("#quantity-field").val().length > 0) {
        var quantity = $("#quantity-field").val();
        if (isInt(quantity)) {
            quantity = parseInt(quantity);
        }
        else {
            quantity = 1;
        }

        if (isInt(quantity) && parseInt(quantity) > 0) {
            $("#quantity-field").val("");
            var barcode = $("#barcode-field").val();
            var article = $("#article-field").val();

            if (barcode.length > 0) {
                $("#barcode-field").val("");
                add_article_row(barcode, quantity);
            } else if (article.length > 0) {
                $("#article-field").val("");
                add_article_row(article, quantity);
            }
        }
        return false;
    }
}

function float_to_str(n){
    return n.toString().replace(/\./g, '_');
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
}

// Draw article name modal

function remove_artname_modal() {
    if ($('#art-name-modal').length > 0) {
        $('#art-name-modal').modal('hide');
        $('#art-name-modal').remove();
    }
}

function draw_artname_modal() {
    if ($('#art-name-modal').length > 0) {
        remove_artname_modal();
    }
    var modal_base = readFile('/js/art-name-modal.html');
    $('#main').append(modal_base);
    $('#art-name-modal').modal('toggle');
    $('#artname-quantity').focus();

}
function artnames_rows(arts){
    var template = '<tr class="art-name-pos selectable"><td>{bc}</td><td>{name}</td><td>{date}</td><td>{price}</td></tr>';
    var top = '<tr class="art-name-pos unselectable"><th>Código</th><th>Nombre</th><th>Fecha</th><th>Precio</th></tr>';
    return top + arts.map(function(o){
        var price = o.costo_caja;

        var html = template.replace('{bc}', o['codigo']).replace('{name}', o['nom_art']).replace('{date}', o['date']).replace('{price}', parseFloat(price).toFixed(2).toString());
        return html;
    }).join('');
}

function artname_table_selects() {
    var i = -1;
    $(document).keydown(function(e) {
        if (e.keyCode === 40 && $('#art-name-modal').length > 0) {
            i = (i + 1 >= $('tr.selectable').length) ? $('tr.selectable').length - 1 : i + 1;
            $('tr.selectable').removeClass('row_selected');
            $('tr.selectable:eq(' + i + ')').addClass('row_selected');
            var name = $('tr.selectable:eq(' + i +')').children()[1].textContent;
            $('#art-name-input').val(name);
            return false;
        } else if (e.keyCode === 38 && $('#art-name-modal').length > 0) {
            i = (i === 0) ? 0 : i - 1;
            $('tr.selectable').removeClass('row_selected');
            $('tr.selectable:eq(' + i + ')').addClass('row_selected');
            var name = $('tr.selectable:eq(' + i +')').children()[1].textContent;
            $('#art-name-input').val(name);
            return false;
        } else if (e.keyCode === 13 && $('#art-name-modal').length > 0 && $('#art-name-input').val().length > 3) {
            var quantity = $('#artname-quantity').val() || '1';
            if (isInt(quantity)) {
                quantity = parseInt(quantity);
            } else {
                quantity = 1;
            }
            var name = $('#art-name-input').val();
            add_article_row(name, quantity);
            remove_artname_modal();
        }
    });
}

function add_artname() {
    if ($('#art-name-modal').length > 0 && $('#art-name-input').val().length > 3) {
        var quantity = $('#artname-quantity').val() || '1';
        if (isInt(quantity)) {
            quantity = parseInt(quantity);
        } else {
            quantity = 1;
        }
        var name = $('#art-name-input').val();
        add_article_row(name, quantity);
        remove_artname_modal();
    }
    return false;

}

function artname_input_listener(){
    if ($('#art-name-input').length > 0) {
        $('#art-name-input').on('keyup', function (e) {
            var code = e.keyCode;
            if (code === 27) {
                remove_artname_modal();
            } else if ($('#art-name-input').val().length === 4 && art_names) {
                var first_three = $('#art-name-input').val();
                var html =
                    artnames_rows(art_names.filter(function(o){
                        if (o['nom_art'].toUpperCase().search(first_three.toUpperCase()) === 0) {
                            return true;
                        }
                    }));
                $('.art-name-pos').remove();
                $('#pos-names').append(html);
                $('#art-name-input').blur();
                artname_table_selects();
            }
        })
    }
}

// Ugly as f copypaste
$(document).ready(function(){
    $.getJSON('/json/all-articles', {}, function(results) {
        art_names = results;
    });

    var search_box_id = "#article-field";
    var jn;
    shortcut.add("F3", function() {
        $("#barcode-field").focus();
    });
    shortcut.add("F4", function() {
        draw_artname_modal();
        artname_input_listener();
    });
    shortcut.add("F10", function() {
        $("#quantity-field").focus();
    });
    shortcut.add("F6", function() {});
    shortcut.add("F2", function() {});
    shortcut.add("F1", function() {});

    shortcut.add("F8", function() {
        $('[data-toggle="switch"]').switchbtn('toggle');
    });

    $(search_box_id).on("keyup change", function () {
        var inp = $(search_box_id).val();
        if (inp.length === 1) {
            jn = json(inp);
            $(search_box_id).typeahead();
            $(search_box_id).data('typeahead').source = jn;
        } else if (inp.length > 1) {
            if (jn != null) {
                $(search_box_id).typeahead();
                $(search_box_id).data('typeahead').source = jn;
            }
        }
    });
    $('#barcode-field').focus();

    $('#quantity-field').tooltip({
        title: 'Cantidad'
    });
    $('#barcode-field').tooltip({
        title: 'Código de barras'
    });
    $('#barcode-field').keydown(function(event) {
        if ( event.ctrlKey || event.altKey )
            event.preventDefault();

    });
    $('#unregistered-quantity').tooltip('show');
    $('#unregistered-price').tooltip('show');
    $('#quantity-field').tooltip('show');
    $('#barcode-field').tooltip('show');
    setTimeout(function() {
        $('#unregistered-quantity').tooltip('hide');
        $('#unregistered-price').tooltip('hide');
        $('#quantity-field').tooltip('hide');
        $('#barcode-field').tooltip('hide');
    }, 5000);
});
