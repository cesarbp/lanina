// Requires jquery and shortcut

$(document).ready(function () {
    $('tr.result').hover(function() {
	var g = this.children[4].children[0].href;
	var v = this.children[5].children[0].href;
	var p = this.children[6].children[0].href;
	var m = this.children[7].children[0].href;
	var e = this.children[8].children[0].href;
	shortcut.add("g", function(){
	    window.location = g;
	});
	shortcut.add("v", function(){
	    window.location = v;
	});
	shortcut.add("p", function(){
	    window.location = p;
	});
	shortcut.add("m", function(){
	    window.location = m;
	});
	shortcut.add("e", function(){
	    window.location = e;
	});
    }, function(){
	var c = this.children[4].children[0].href;
	var m = this.children[5].children[0].href;
	var e = this.children[6].children[0].href;
	shortcut.remove("g");
	shortcut.remove("v");
	shortcut.remove("p");
	shortcut.remove("m");
	shortcut.remove("e");
    });
});
