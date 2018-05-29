/* globals $ */

$(document).ready(function() {

  /* Classes */

  // Toggle class list
  var subjectListItems = $("#subject-list li");
  var subjectListSmallTexts = $("#subject-list small");
  var classLists = $(".class-lists");
  var lastClicked = -1;
  subjectListItems.click(function() {
    subjectListItems.each(function() {
      $(this).removeClass("active");
    });
    subjectListSmallTexts.each(function() {
      $(this).addClass("text-muted");
    });
    classLists.each(function() {
      $(this).addClass("gone");
    });
    var index = $(this).index();
    if (index != lastClicked) {
      var clickedListItem = $(subjectListItems[index]);
      var clickedSmallText = $(subjectListSmallTexts[index]);
      var classList = $(classLists[index]);
      clickedListItem.addClass("active");
      clickedSmallText.removeClass("text-muted");
      classList.removeClass("gone");
      lastClicked = index;
    } else {
      lastClicked = -1;
    }
  });

});
