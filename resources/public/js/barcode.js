// FIXME - Invalid html ids
var art_names;
var audio;
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

function blink(id) {
    var n = 0;
    var colors = ["white", "black"];
    var changeColor = function() {
        $(id).css('color', colors[n]);
        n = n + 1;
        n = n % 2;
    };
    return setInterval(changeColor, 250);
}

function removeAlert() {
    var id = "#alert";
    if ( $(id).length > 0 )
        $(id).remove();
    return 0;
}

function playAlert() {
    audio.load();
    audio.play();
    return 0;
}

function addAlert(text) {
    var id = "#alert";
    removeAlert();
    var alertHtml = '<li id="alert" style="position:relative;left:10px;"><h3>' + text + '<h3></li>';
    $("#subnav").append(alertHtml);
    playAlert();
    return blink(id);
}

function article_row(article, quantity) {
    var id = article._id;
    var price = article.precio_venta;

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
    var quants = articles.map(function (r) {
        return parseInt(r.children[1].innerHTML);
    });

    var total = totals.reduce(function (a,b) {return a + b;});
    var quant = quants.reduce(function (a,b) {return a + b;});
    return [total, quant];
}

function update_total() {
    setTimeout(function() {
        var total = calculate_total();
        if ( total[0] )
        {
            $("#total").text('Total: ' + total[0].toFixed(2).toString());
            $("#number").text('#arts: ' + total[1]);
        }
        else
        {
            $("#total").text('Total: 0.00');
            $("#number").text('#arts: 0');
        }

    }, 200);
}

function get_ticket_number() {
    return parseInt($("#ticketn").html());
}

// actually bd id not barcode
function ticket_links(barcode, quantity, increase) {
    var ticket_link = $("#ticket");
    var bill_link = $("#bill");
    barcode = barcode.replace(/\s/g, '_');
    var req_html =  barcode + "=" + quantity;
    var prev_quant = increase ? quantity - 1 : quantity + 1;
    var prev_html = barcode + "=" + prev_quant.toString();
    var ticket_href = '/tickets/nuevo/';

    if (ticket_link.length !== 0 && bill_link.length !== 0) {
        ticket_link_href = ticket_link[0].href;
        bill_link_href = bill_link[0].href;
        if (ticket_link_href.search("\\?" + prev_html) >= 0 || ticket_link_href.search("&" + prev_html) >= 0){
            $("#ticket")[0].href = $("#ticket")[0].href.replace("?" + prev_html, "?" + req_html);
            $("#ticket")[0].href = $("#ticket")[0].href.replace("&" + prev_html, "&" + req_html);
            $("#bill")[0].href = $("#bill")[0].href.replace("?" + prev_html, "?" + req_html);
            $("#bill")[0].href = $("#bill")[0].href.replace("&" + prev_html, "&" + req_html);
        } else {
            $("#ticket")[0].href = ticket_link_href + "&" + req_html;
            $("#bill")[0].href = bill_link_href + "&" + req_html;
        }
    }
    else
    {
        var ticketn = get_ticket_number();
        var ticket_html = '<a id="ticket" on-click="draw_modal();" class="btn btn-primary" href="/tickets/nuevo/?ticketn=' + ticketn +
                '&' + req_html + '">F9 - Generar Ticket</a>';
        var bill_html = '<a id="bill" class="btn btn-success" href="/facturas/nuevo/?ticketn=' + ticketn +
                '&' + req_html + '">Generar Factura</a>';
        var form_html = '<div id="gen-tickets" class="form-actions">' + ticket_html + bill_html + '</div>';
        $("#main").append(form_html);
        $('#ticket').click(function() {
            draw_modal();
            return false;
        });
        // $('#bill').click(function() {
        //     draw_modal();
        //     return false;
        // });
        shortcut.add("F9", function() {
            draw_modal();
        });
    }
    return 0;
}

function remove_ticket_links(barcode) {
    var ticket_link = $("#ticket")[0];
    var bill_link = $("#bill")[0];

    if ( ticket_link )
    {
        var re = new RegExp ('[?|&]' + barcode + '=\\d+');
        ticket_link.href = ticket_link.href.replace(re, '');
        bill_link.href = bill_link.href.replace(re, '');

        if (ticket_link.href.search(/&/) === -1)
        {
            $('#gen-tickets').remove();
            shortcut.add("F9", function() {
                return false;
            });
        }
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

function parseUnregistered(s) {
    console.log(s);
    var type = s.substring(0, 4);
    if ( type != "exto" && type != "gvdo" )
        return false;
    var re = new RegExp(type + "\\d+");
    var newS = s.replace(re, "");
    var nom_art;
    var matches;
    var price;
    if ( '-' == newS.substring(0, 1) )
    {
        re = /-([A-Za-z0-9-]+)_(\d+_*\d*)/;
        matches = re.exec(newS);
        nom_art = matches[1].replace(/-/g, " ");
        price = matches[2].replace(/_/, ".");
        price = parseFloat(price);
    }
    else
    {
        console.log(newS);
        re = /_(\d+_*\d*)/;
        matches = re.exec(newS);
        nom_art = type == "exto" ? "ARTÍCULO EXENTO" : "ARTÍCULO GRAVADO";
        price = matches[1].replace(/_/, ".");
        price = parseFloat(price);
    }

    var article = {
        _id : s,
        nom_art : nom_art,
        precio_venta : price
    };

    return article;
}

function add_article_row(denom, n) {
    var quantity;
    var price;
    var new_quantity;
    var article;

    removeAlert();

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
                            {
                                var unr = parseUnregistered(denom);
                                if ( unr == false )
                                {
                                    addAlert("No encontrado:" + denom);
                                    $("#barcode-field").val("");
                                }
                                else
                                    article = unr;
                            }

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
        $(article_row(article, n)).prependTo("table > tbody");
        //$("#articles-table").append(article_row(article, n));
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
    return true;
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
    return true;
}

// Type can be "exto" or "gvdo"
function next_unregistered(type) {
    var rows = $("#articles-table").find('tr.article-row');

    var unregistered_rows = rows.filter(function(ind) {
        if (rows[ind].id.substr(0,4) === type)
            return true;
        return false;
    });

     if (unregistered_rows.length === 0) {
        return type + "1";
    }

    var nums = unregistered_rows.map(function(ind) {
        return parseInt(unregistered_rows[ind].id.substr(4));
    });

    return type + (nums.sort()[nums.length - 1] + 1).toString();
}

function float_to_str(n){
    return n.toString().replace(/\./g, '_');
}

function add_unregistered() {
    if ($("#unregistered-price").val().length > 0) {
        var price = parseFloat($("#unregistered-price").val());
        var quantity = $("#unregistered-quantity").val() || 1;
        var checkbox = $('input[name=gravado]')[0];
        var name = $('#unregistered-name').val().toUpperCase();
        var type;
        var precio_venta;
        var nom_art;

        if ( checkbox.checked ) {
            type = "gvdo";
            precio_venta = price;
            nom_art = name == "" ? "ARTÍCULO GRAVADO" : name;
        }
        else {
            type = "exto";
            precio_venta = price;
            nom_art = name == "" ? "ARTÍCULO EXENTO" : name;
        }
        if ( price ) {
            var bc;
            if ( name == "" )
                bc = next_unregistered(type) + '_' + float_to_str(price);
            else
                bc = next_unregistered(type) + '-' + name.replace(/\s+/g, "-") +
                     '_' + float_to_str(price);
            var article = {
                _id: bc,
                nom_art: nom_art,
                precio_venta: precio_venta
            };
            $(article_row(article, quantity)).prependTo("table > tbody");
            ticket_links(bc, quantity, true);
            $("#unregistered-price").val("");
            $("#unregistered-quantity").val("");
            $("#unregistered-name").val("");
            $("#barcode-field").focus();
            setTimeout(function() {
                update_total();
            }, 200);
        }
    }
}

function unregistered_listener(field, e) {
    var code = e.keyCode || e.which;
    if (code === 13) {
        add_unregistered();
        return false;
    }
    return true;
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

// Generate the print ticket/bill modal

// Takes an anchor returns an array of products - [bc, name, quantity, unit price, total]
function split_url (a) {
    var req = a.search,
    re = /(?:\?|&(?:amp;)?)([^=&#]+)(?:=?([^&#]*))/g,
    match = [],
    res = [],
    getType = function(denom) {
        if ( denom.search('exto') === 0 )
            return 'exto';
        else if ( denom.search('gvdo') === 0 )
            return 'gvdo';
        else
            return 'id';
    },
    getPrice = function(denom) {
        var type = getType(denom);
        var price;
        if ( type === 'id' )
            $.ajax({url: '/json/article/id', dataType: 'json', data: {id: denom}, async: false, success: function(article) {
                if ( article && article._id )
                {
                    price = article.precio_venta;
                }
            }});
        else
        {
            art = parseUnregistered(denom);
            price = art.precio_venta;
        }
        return price;
    };
    var denom, quant, type, price, name, bc;
    while ( (match = re.exec(req)) )
    {
        denom = match[1];
        if ( denom === 'ticketn' ) continue;
        quant = parseInt(match[2]);
        type = getType(denom);
        price = getPrice(denom);
        name = '';
        bc = '';
        if ( type === 'id' )
            $.ajax({url: '/json/article/id', dataType: 'json', data: {id: denom}, async: false, success: function(article) {
                if (article && article._id)
                    name = article.nom_art;
            }});
        else
        {
            var article = parseUnregistered(denom);
            bc = '0';
            name = article.nom_art;
        }

        res.push([bc, name, quant, price, quant * price]);
    }
    return res;
}
// Draw the modal

// Print the ticket
function print_ticket() {
    var pay = $('#pay').val();
    var total = parseFloat($('#modallabel')[0].textContent.replace('Total a pagar: ', ''));
    if (pay && parseFloat(pay) >= total) {
        $('#print-ticket')[0].href = $('#print-ticket')[0].href + '&pay=' + pay;
        window.location = $('#print-ticket')[0].href;
    }  else if ($('#error-popup').length === 0) {
        var pp = parseFloat(pay);
        if ( !pp )
            pp = 0;
        var diff = total - pp;
        diff = diff.toFixed(2).toString();
        $('.modal-header').append(
            '<div id="error-popup" class="alert alert-block alert-error">' +
                '<button type="button" class="close" data-dismiss="alert">×</button>' +
                '<h3>Falta:' + diff + '</h3></div>');
        $('#error-popup').alert();
        $('#pay').val(total.toString());
        //$('.modal-footer').addClass('error');
    }
}

function pay_listeners() {
    /*
    $('#pay').keypress(function(e) {
        code = (e.keyCode ? e.keyCode : e.which);
        if (code === 13) {
            print_ticket();
            return false;
        }
        return true;
    });
     */
    $('#pay').blur(function() {
        var pay = $('#pay').val();
        var total = parseFloat($('#modallabel')[0].textContent.replace('Total a pagar: ', ''));
        if (pay && parseFloat(pay) >= total) {
            $('.modal-footer').removeClass('error');
            $('#print-ticket').attr('disabled', false);
            $('#print-ticket').click(function(){return true;});
            //$('#print-ticket')[0].href = $('#print-ticket')[0].href + '&pay=' + pay;
            if ($('#error-popup').length > 0) {
                $('#error-popup').alert('close');
            }
        }  else {
            $('#print-ticket').attr('disabled', true);
            $('#print-ticket').click(function(){return false;});
            $('.modal-footer').addClass('error');
            if ($('#error-popup').length === 0) {
                $('.modal-header').append(
                    '<div id="error-popup" class="alert alert-block alert-error">' +
                        '<button type="button" class="close" data-dismiss="alert">×</button>' +
                        '<h1>No hay cantidad o cantidad insuficiente</h1></div>');
                $('#error-popup').alert();
            }
            $('#pay').focus();
        }
    });
}

function remove_modal() {
    $("#modal").modal('hide');
    $("#modal").remove();
    shortcut.remove("F8");
    shortcut.add("F8", function() {
        $('[data-toggle="switch"]').switchbtn('toggle');
    });
}

function draw_modal () {
    if ($("#modal").length > 0) {
        remove_modal();
    }

    var a = $('#ticket')[0];
    var articles = split_url(a);

    var art_base = '<tr><td>{name}</td><td>{quant}</td><td>{price}</td><td>{art-total}</td></tr>';
    var name_base = '<tr><td>{name}</td></tr>';
    var desc_base = '<tr><td>{quant} x {price} = {total}</td></tr>';
    var modal_base = readFile('/js/modal.html');
    var rows = "";
    var total = 0;
    var art_row;
    for (var i = 0; i < articles.length; i++) {
        art_row = art_base.replace('{name}', articles[i][1]).replace('{quant}', articles[i][2]).replace('{price}', articles[i][3].toFixed(2).toString()).replace('{art-total}', articles[i][4].toFixed(2).toString());
        rows = rows + art_row + '\n';
        total = total + articles[i][4];
    }

    modal_html = modal_base.replace(/{total}/g, total.toFixed(2).toString()).replace('{rows}', rows).replace('{req}', a.search);
    $("#main").append(modal_html);
    $("#modal").modal('toggle');

    shortcut.add("F8", function() {
        $("#pay").focus();
    });
    $('#pay').focus();

    $('#pay').keyup(function(e){
        if ( e.keyCode === 13 )
        {
            print_ticket();
            return false;
        }
        return true;
    });
    $('#print-ticket').click(function(){return false;});
    pay_listeners();
    return false;
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
        var price = o.precio_venta;

        var html = template.replace('{bc}', o['codigo']).replace('{name}', o['nom_art']).replace('{date}', o['date']).replace('{price}', parseFloat(price).toFixed(2).toString());
        return html;
    }).join('');
}

function artname_table_selects() {
    var i = 0;
    $('tr.selectable:eq(0)').addClass('row_selected');
    var name = $('tr.selectable:eq(0)').children()[1].textContent;
    $('#art-name-input').val(name);
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
            $('#barcode-field').focus();
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
        });
    }
}

// Ugly as f copypaste
$(document).ready(function(){

    var trie = {};
    var jn;

    $.getJSON('/json/all-articles', {}, function(results) {
        art_names = results;
    });

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
    shortcut.add("F2", function() {});
    shortcut.add("F1", function() {});
    shortcut.add("F7", function() {
        $("#unregistered-name").focus();
    });
    shortcut.add("F5", function() {
        $("#unregistered-quantity").focus();
    });
    shortcut.add("F6", function() {
        $("#unregistered-price").focus();
    });
    shortcut.add("F8", function() {
        $('[data-toggle="switch"]').switchbtn('toggle');
    });

    $('#barcode-field').focus();

    $('#unregistered-quantity').tooltip({
        title: 'Cantidad'
    });
    $('#unregistered-price').tooltip({
        title: 'Precio'
    });
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

    audio = document.createElement('audio');
    audio.setAttribute('src', '/sound/beep.mp3');

});
