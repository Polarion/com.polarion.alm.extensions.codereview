var currentScroll = null;

onload = function(){
	renderNav();
	$(".cr_file_label").stick_in_parent();
};

function renderNav() {
	var navContainer = document.createElement("div");
	navContainer.className = "cr_nav_container";
	document.body.appendChild(navContainer);
	appendPrev(navContainer);
	appendNext(navContainer);
	appendTop(navContainer);
}

function scrollTo(nodeId){
	var elementToScroll = document.getElementById(nodeId);
	var nodes = document.getElementsByClassName("change");
	var index = Array.prototype.indexOf.call(nodes, elementToScroll);
	currentScroll = nodes[index];
	performScrollToElement(currentScroll);
}

function performScrollToElement(element){
	$('html,body').animate({scrollTop: $(element).offset().top-40}, {duration:0});
}

function scrollNext() {
	var scrollTop = document.body.scrollTop;
	var nodes = document.getElementsByClassName("change");
	var startIndex = Array.prototype.indexOf.call(nodes, currentScroll);

	startIndex++;
	
	if (startIndex < nodes.length && startIndex >=0) {
		currentScroll = nodes[startIndex];
		performScrollToElement(currentScroll);
		if (document.body.scrollTop == scrollTop) {
			scrollNext();
		}
	}
}

function scrollPrev() {
	var scrollTop = document.body.scrollTop;
	var nodes = document.getElementsByClassName("change");
	var startIndex = Array.prototype.indexOf.call(nodes, currentScroll);

	startIndex--;
	
	if (startIndex < nodes.length && startIndex >=0) {
		currentScroll = nodes[startIndex];
		performScrollToElement(currentScroll);
		if (document.body.scrollTop == scrollTop) {
			scrollPrev();
		}
	}
}

function scrollTop() {
	currentScroll = null;
	document.body.scrollTop = 0;
}

function appendNext(navContainer) {
	var button = document.createElement("div");
	button.innerHTML="Next";
	appendHandler(button, scrollNext);
	button.className = "cr_nav_button";
	navContainer.appendChild(button);
}

function appendPrev(navContainer) {
	var button = document.createElement("div");
	button.innerHTML="Prev";
	appendHandler(button, scrollPrev);
	button.className = "cr_nav_button";
	navContainer.appendChild(button);
}

function appendTop(navContainer) {
	var button = document.createElement("div");
	button.innerHTML="Top";
	appendHandler(button, scrollTop);
	button.className = "cr_nav_button";
	navContainer.appendChild(button);
}



function appendHandler(el, yourFunction) {
	if (el.addEventListener) {
	    el.addEventListener("click", yourFunction, false);
	} else {
	    el.attachEvent('onclick', yourFunction);
	}
}