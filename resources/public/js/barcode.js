// FIXME - Invalid html ids
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

function article_row(article, quantity) {

    if (article.codigo === "0") {
	var barcode = article.nom_art.replace(/\s/g, '_');
    } else {
	var barcode = article.codigo
    }

    var name = article.nom_art;
    if (article.prev_con !== 0) {
	var price = article.prev_con;
    }
    else {
	var price = article.prev_sin;
    }
    var total = price * parseInt(quantity);

    if (!barcode) {
	return false;
    }
    var html = '<tr class="article-row" id=' + barcode.toString() + '><td class="art_name">' +
	name.toString() + '<td class="art_quantity">' +
	quantity.toString() + '</td><td class="art_price">' +
	price.toFixed(2).toString() + '</td>' + '</td><td class="art_total">' +
	total.toFixed(2).toString() + '</td>' +
	'<td><a class="btn" onclick="add_article_row(\'' + barcode + '\',1);"><i class="icon-chevron-up"></i></a>' +
	'<a class="btn" onclick="add_article_row(\'' + barcode + '\',-1);"><i class="icon-chevron-down"></i></a></td>' +
	'<td><a class="btn btn-danger" onclick="remove_article_row(\'' + barcode + '\');"><i class="icon-remove"></i></a></td></tr>';
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
    } else {
	var ticket_html = '<a id="ticket" class="btn btn-primary" href="/tickets/nuevo/?' +
	    req_html + '">F9 - Generar Ticket</a>';
	var bill_html = '<a id="bill" class="btn btn-success" href="/facturas/nuevo/?' +
	    req_html + '">F10 - Generar Factura</a>';
	var form_html = '<div id="gen-tickets" class="form-actions">' + ticket_html + bill_html + '</div>';
	$("#main").append(form_html);
	$('#ticket').click(function() {
	    draw_modal();
	    return false;
	});
	$('#bill').click(function() {
	    draw_modal();
	    return false;
	});
	shortcut.add("F9", function() {
	    draw_modal();
	});
    }
    return 0;
}

function remove_ticket_links(barcode) {
    var ticket_link = $("#ticket")[0];
    var bill_link = $("#bill")[0];
    
    if (ticket_link) {
	var re = new RegExp ('[?|&]' + barcode + '=\\d+');
	ticket_link.href = ticket_link.href.replace(re, '');
	bill_link.href = bill_link.href.replace(re, '');

	if (-1 === ticket_link.href.search(/\?/)) {
	    if (ticket_link.href.search(/&/) !== -1) {
		ticket_link.href = ticket_link.href.replace(/&/, '?');
		bill_link.href = bill_link.href.replace(/&/, '?');
	    }
	    else {
		$('#gen-tickets').remove();
	    }
	}
	shortcut.add("F9", function() {
	    return false;
	});

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

function add_article_row(barcode, n) {
    var worked = false;
    var id_barcode = "#" + barcode.replace(/\s/g, '_');
    if (isInt(barcode) || $(id_barcode).length !== 0) {
	if ($(id_barcode).length !== 0) {
	    var quantity = $(id_barcode).children()[1].innerHTML;
	    var price = $(id_barcode).children()[2].innerHTML;
	    var new_quantity = n + parseInt(quantity);
	    if (0 >= new_quantity) {
		remove_article_row(barcode);
		return 0;
	    }
	    var total = parseFloat(price) * new_quantity;
	    $(id_barcode).children()[1].innerHTML = new_quantity.toString();
	    $(id_barcode).children()[3].innerHTML = total.toFixed(2).toString();
	    if ($(id_barcode).length !== 0)
		ticket_links(barcode, new_quantity, (n > 0));
	}
	else {
	    $.getJSON('/json/article', {'barcode': barcode}, function(article) {
		if (article && article.codigo) {
		    $("#articles-table").append(article_row(article, n));
		    worked = true;
		}
	    });
	    setTimeout(function() {
		if (worked) {
		    ticket_links(barcode, n, true);
		}
	    }, 200);
	}
	$("#barcode-field").val("");
	update_total();
    }
    else {
	var codigo = "";
	$.getJSON('/json/article-name', {'name': barcode}, function(article) {
	    if (article && article.codigo) {
		$("#articles-table").append(article_row(article, n));
		worked = true;
		codigo = article.codigo;
	    }
	});
	setTimeout(function() {
	    if (worked) {
		
		if (codigo === '' || codigo === "0") {
		    ticket_links(barcode, n, true);
		} else {
		    ticket_links(codigo, n, true);
		}

	    }
	}, 200);
	update_total();
    }
}

function barcode_listener (field, e) {
    var code = e.keyCode || e.which;
    if (code == 13) {
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
    } else {
	return true;
    }
}

function article_listener (field, e) {
    var code = e.keyCode || e.which;
    if (code == 13 && $("#article-field").val().length > 0) {
	var name = $("#article-field").val();
	
	var quantity = $("#quantity-field").val() || 1;
	$("#quantity-field").val("");
	$("#article-field").val("");
	if (isInt(quantity)) {
	    quantity = parseInt(quantity);
	}
	else {
	    quantity = 1;
	}

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

// Type can be "exto" or "gvdo"
function next_unregistered(type){
    var rows = $("#articles-table").find('tr.article-row');
    
    var unregistered_rows = rows.filter(function(ind) {
	if (rows[ind].id.substr(0,4) === type) {
	    return true;
	}
    });

     if (unregistered_rows.length === 0) {
	return type + "1";
    }

    var nums = unregistered_rows.map(function(ind) {
	return parseInt(unregistered_rows[ind].id.substr(4));
    })

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
	if (checkbox.checked) {
	    var type = "gvdo";
	    var prev_con = price;
	    var prev_sin = 0;
	    var nom_art = "ARTÍCULO GRAVADO";
	}
	else {
	    var type = "exto";
	    var prev_sin = price;
	    var prev_con = 0;
	    var nom_art = "ARTÍCULO EXENTO";
	}
	if (price) {
	    var bc = next_unregistered(type) + '_' + float_to_str(price);
	    var article = {
		codigo: bc,
		nom_art: nom_art,
		prev_con: prev_con,
		prev_sin: prev_sin
	    };
	    $("#articles-table").append(article_row(article, quantity));
	    ticket_links(bc, quantity, true);
	    $("#unregistered-price").val("");
	    $("#unregistered-quantity").val("");
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

// Generate the print ticket/bill modal

// Takes an anchor returns an array of products - [bc, name, quantity, unit price, total]
function split_url (a) {
    var req = a.search,
    re = /(?:\?|&(?:amp;)?)([^=&#]+)(?:=?([^&#]*))/g,
    match = [],
    res = [],
    getType = function(denom) {
	if (denom.search('exto') === 0) {
	    return 'exto'; 	// No tax
	} else if (denom.search('gvdo') === 0) {
	    return 'gvdo';	// Taxed
	} else if (isInt(denom)) {
	    return 'bc';	// Barcode
	} else {
	    return 'nom';	// Name
	}
    },
    getPrice = function(denom) {
	var type = getType(denom);
	var price;
	if (type === 'nom') {
	    $.ajax({url: '/json/article-name', dataType: 'json', data: {name: denom.replace(/_/g, ' ')}, async: false, success: function(article) {
		if (article && article.codigo) {
		    if (article.prev_con >= 0) {
			price = article.prev_con;
		    } else {
			price = article.prev_sin;
		    }
		}
	    }});
	} else if (type == 'gvdo') {
	    price = parseFloat(denom.replace(/gvdo\d+_/, '').replace(/_/, '.'));
	} else if (type == 'exto') {
	    price = parseFloat(denom.replace(/exto\d+_/, '').replace(/_/, '.'));
	} else {
	    $.ajax({url: '/json/article', dataType: 'json', data: {barcode: denom}, async: false, success: function(article) {
		if (article && article.codigo) {
		    if (article.prev_con > 0) {
			price = article.prev_con;
		    } else {
			price = article.prev_sin;
		    }		    
		}
	    }});
	}
	return price;
    };
    var denom, quant, type, price, name, bc;
    while (match = re.exec(req)) {
	denom = match[1],
	quant = parseInt(match[2]),
	type = getType(denom),
	price = getPrice(denom),
	name = '';
	bc = '';
	if (type === 'bc') {
	    $.ajax({url: '/json/article', dataType: 'json', data: {barcode: denom}, async: false, success: function(article) {
		if (article && article.codigo) {
		    name = article.nom_art;
		}
	    }});
	} else if (type === 'nom') {
	    bc = '0';
	    name = denom;
	} else if (type === 'gvdo') {
	    bc = '0';
	    name = 'Artículo Gravado';
	} else {
	    bc = '0';
	    name = 'Artículo Exento';
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
	$('.modal-header').append(
	    '<div id="error-popup" class="alert alert-block alert-error">' +
		'<button type="button" class="close" data-dismiss="alert">×</button>' +
		'<h1>No hay cantidad o cantidad insuficiente</h1></div>');
	$('#error-popup').alert();
	$('.modal-footer').addClass('error');
    }
}

function pay_listeners() {
    $('#pay').keypress(function(e) {
        code= (e.keyCode ? e.keyCode : e.which);
        if (code === 13) {
	    print_ticket();
	}
    });
    $('#pay').blur(function() {
	var pay = $('#pay').val();
	var total = parseFloat($('#modallabel')[0].textContent.replace('Total a pagar: ', ''));
	if (pay && parseFloat(pay) >= total) {
	    $('.modal-footer').removeClass('error');
	    $('#print-ticket').attr('disabled', false);
	    $('#print-ticket').click(function(){return true;});
	    $('#print-ticket')[0].href = $('#print-ticket')[0].href + '&pay=' + pay;
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
    $('#art-name-input').focus();
    
}
function artnames_rows(arts){
    var template = '<tr class="art-name-pos selectable"><td>{bc}</td><td>{name}</td><td>{date}</td><td>{price}</td></tr>';
    var top = '<tr class="art-name-pos unselectable"><th>Código</th><th>Nombre</th><th>Fecha</th><th>Precio</th></tr>';
    return top + arts.map(function(o){
	var price;
	var con = o['prev_con'];
	var sin = o['prev_sin'];
	if (parseFloat(con) > 0) {
	    price = con;
	} else {
	    price = sin;
	}

	var html = template.replace('{bc}', o['codigo']).replace('{name}', o['nom_art']).replace('{date}', o['fech_an']).replace('{price}', parseFloat(price).toFixed(2).toString());
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
    })
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
    
    var trie = {};
    var search_box_id = "#article-field";
    var jn;
    shortcut.add("F3", function() {
	$("#barcode-field").focus();
    });
    shortcut.add("F4", function() {
	draw_artname_modal();
	artname_input_listener();
    });
    shortcut.add("F2", function() {
	$("#quantity-field").focus();
    });
    shortcut.add("F5", function() {});
    shortcut.add("F7", function() {
	$("#unregistered-price").focus();
    });
    shortcut.add("F6", function() {
	$("#unregistered-quantity").focus();
    });
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

