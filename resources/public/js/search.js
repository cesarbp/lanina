// Requires jquery, jquery-ui and trie.js
var jn;
var res_url = "/json/article/starts_with";
var prov_url = "/json/all-providers";

var json = function (first_letter) {
    var res = [];
    $.getJSON(res_url, {'letter': first_letter}, function(results) {
	$.each(results, function(k, v) {
	    res.push(v["nom_art"]);
	});
    });
    return res;
}

var json2 = function () {
    $.getJSON(prov_url, {}, function(results) {
	var providers = results;

	$('#provider-name').typeahead();
	$('#provider-name').data('typeahead').source = results;
    });
}

$(document).ready(function(){
    json2();
    jn = $.getJSON('/json/all-articles', {}, function(results) {
	jn = results.map(function(o) {
	    return o['nom_art'];
	});
    })
    var trie = {};
    var search_box_id = "#search";
    $('#search').focus();
    
    $(search_box_id).on("keyup change", function () {
	var inp = $(search_box_id).val();

	if (inp.length === 1) {
	    $(search_box_id).typeahead();
	    $(search_box_id).data('typeahead').source = jn;
	} else if (inp.length > 1) {
	    if (jn != null) {
		$(search_box_id).typeahead();
		$(search_box_id).data('typeahead').source = jn;
	    }
	}
    });
});
