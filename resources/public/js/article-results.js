// Requires jquery and shortcut

$(document).ready(function () {
    $('tr.result').hover(function() {
	var g = this.children[4].children[0].href;
	var v = this.children[5].children[0].href;
	var p = this.children[6].children[0].href;
	var s = this.children[7].children[0].href;
	var c = this.children[8].children[0].href;
	var n = this.children[9].children[0].href;
	var t = this.children[10].children[0].href;
	var a = this.children[11].children[0].href;
	var e = this.children[12].children[0].href;
	shortcut.add("g", function(){
	    window.location = g;
	});
	shortcut.add("v", function(){
	    window.location = v;
	});
	shortcut.add("p", function(){
	    window.location = p;
	});
	shortcut.add("s", function(){
	    window.location = s;
	});
	shortcut.add("c", function(){
	    window.location = c;
	});
	shortcut.add("n", function(){
	    window.location = n;
	});
	shortcut.add("t", function(){
	    window.location = t;
	});
	shortcut.add("a", function(){
	    window.location = a;
	});
	shortcut.add("e", function(){
	    window.location = e;
	});
    }, function(){
	shortcut.remove("g");
	shortcut.remove("v");
	shortcut.remove("p");
	shortcut.remove("s");
	shortcut.remove("c");
	shortcut.remove("n");
	shortcut.remove("t");
	shortcut.remove("a");
	shortcut.remove("e");
    });
});
