/* globals $ */

import {forms} from "./forms.js";
import {classes} from "./classes.js";
import {calendar} from "./calendar.js";

$(document).ready(function() {

  // Add active class to navigation tab
  var activeIndex= parseInt($("active").first().text());
  if (activeIndex != -1) {
    var activeTab = $("#navigation-tabs").children()[activeIndex];
    $(activeTab).addClass("active");
  }

  // Close flash messages automatically
  setTimeout(function() {
    $(".alert").alert("close");
  }, 3000);

  forms();
  classes();
  calendar();

});
