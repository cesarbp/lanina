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

function article_row(article, quantity) {
    if (article.codigo === "0") {
	var barcode = article.nom_art.replace(/\s+/g, '_');
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
    barcode = barcode.replace(/\s+/g, '_');
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
	    req_html + '">Generar Ticket</a>';
	var bill_html = '<a id="bill" class="btn btn-success" href="/facturas/nuevo/?' +
	    req_html + '">Generar Factura</a>';
	var form_html = '<div id="gen-tickets" class="form-actions">' + ticket_html + bill_html + '</div>';
	$("#main").append(form_html);
    }
    return 0;
}

function remove_ticket_links(barcode) {
    var ticket_link = $("#ticket")[0];
    var bill_link = $("#bill")[0];
    
    if (ticket_link) {
	var re = new RegExp ('[?|&]' + barcode.replace(/\s+/g, '_') + '=\\d+');
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
    }
}

function remove_article_row(barcode) {
    var id_barcode = "#" + barcode.replace(/\s+/g, '_');
    remove_ticket_links(barcode);
    if ($(id_barcode)) {
	$(id_barcode).remove();
	remove_ticket_links(barcode);
	update_total();
    }
}

function add_article_row(barcode, n) {
    var worked = false;
    var id_barcode = "#" + barcode.replace(/\s+/g, '_');    
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
		if (worked)
		    ticket_links(barcode, 1, true);
	    }, 200);
	}
	$("#barcode-field").val("");
	update_total();
    }
    else {
	$.getJSON('/json/article-name', {'name': barcode}, function(article) {
	    if (article && article.codigo) {
		$("#articles-table").append(article_row(article, n));
		worked = true;
	    }
	});
	setTimeout(function() {
	    if (worked)
		ticket_links(barcode, 1, true);
	}, 200);
	update_total();
    }
}

function barcode_listener (field, e) {
    var code = e.keyCode || e.which;
    if (code == 13) {
	var barcode = $("#barcode-field").val();
	var quantity = $("#quantity-field").val() || 1;
	$("#quantity-field").val("1");
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
	$("#quantity-field").val("1");
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
	    $("#quantity-field").val("1");
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
	    var nom_art = "Artículo Gravado";
	}
	else {
	    var type = "exto";
	    var prev_sin = price;
	    var prev_con = 0;
	    var nom_art = "Artículo Exento";
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
	    ticket_links(bc, 1, true);
	    $("#unregistered-price").val("");
	    $("#unregistered-quantity").val("1");
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

$(document).ready(function(){
    var trie = {};
    var search_box_id = "#article-field";
    var jn;
    shortcut.add("F2", function() {
	$("#barcode-field").focus();
    });
    shortcut.add("F3", function() {
	$("#article-field").focus();
    });
    shortcut.add("F4", function() {
	$("#quantity-field").focus();
    });
    shortcut.add("F5", function() {});
    shortcut.add("F6", function() {
	$("#unregistered-price").focus();
    });
    shortcut.add("F7", function() {
	$("#unregistered-quantity").focus();
    });
    shortcut.add("F8", function() {
	$('[data-toggle="switch"]').switchbtn('toggle');
    });
    
    $(search_box_id).on("keyup change", function () {
	var inp = $(search_box_id).val();

	if (inp.length === 1) {
	    jn = json(inp);
	    $(search_box_id).autocomplete({source: jn});
	} else if (inp.length > 1) {
	    if (jn != null)
		$(search_box_id).autocomplete({source: jn});
	}
    });

});

