/* globals $, moment */

export function calendar() {

  var calendar = $("#calendar");
  var sources = {
    month: {
      url: "/calendar/month/events",
      type: "GET",
      error: function() {
        console.log("Failed to fetch calendar month events");
      }
    },
    agendaWeek: {
      url: "/calendar/week/events",
      type: "GET",
      error: function() {
        console.log("Failed to fetch calendar week events");
      }
    },
    listWeek: {
      url: "/calendar/list/events",
      type: "GET",
      error: function() {
        console.log("Failed to fetch calendar list events");
      }
    },
  };
  var lastView = "";
  var eventModal = $("#event-modal");
  var eventModalSubject = $("#event-modal-subject");
  var eventModalClass = $("#event-modal-class");
  var eventModalDate = $("#event-modal-date");
  var eventModalTime = $("#event-modal-time");
  var eventModalVenue = $("#event-modal-venue");
  var eventModalCancelButton = $("#event-modal-cancel");
  var eventModalReplaceButton = $("#event-modal-replace");

  // Disable scrolling when event modal is showing
  eventModal.on("show.bs.modal", function() {
    $("html").css({
      overflow: "hidden"
    });
  });
  eventModal.on("hide.bs.modal", function() {
    $("html").css({
      "overflow": "auto"
    });
  });

  // Initialize tooltip
  $("#event-modal-subject").tooltip();

  // Initialize calendar
  calendar.fullCalendar({
    schedulerLicenseKey: "CC-Attribution-NonCommercial-NoDerivatives",
    eventSources: [
      sources.month,
      sources.agendaWeek,
      sources.listWeek
    ],
    header: {
      left: "month,agendaWeek,listWeek",
      center: "title"
    },
    buttonText: {
      month: "Month",
      agendaWeek: "Week",
      listWeek: "List",
      today: "Today"
    },
    nowIndicator: true,
    businessHours: {
      dow: [1, 2, 3, 4, 5, 6],
      start: "08:00",
      end: "18:00"
    },
    minTime: "08:00:00",
    maxTime: "18:00:00",
    contentHeight: "auto",
    eventLimit: true,
    timeFormat: "hh:mmA",
    views: {
      agendaWeek: {
        columnHeaderFormat: "ddd, D/M",
        slotLabelFormat: "hh:mmA",
        allDayText: "All Day"
      },
      listWeek: {
        listDayAltFormat: "MMM D, YYYY",
        noEventsMessage: "Nothing to display"
      }
    },
    slotEventOverlap: false,
    viewRender: function(view) {
      if (view.name != lastView) {
        // Change event source
        calendar.fullCalendar("removeEventSources");
        calendar.fullCalendar("addEventSource", sources[view.name]);
        lastView = view.name;
      }
    },
    eventAfterRender: function(event, element) {
      if (!moment(event.start).isSameOrBefore(moment())) {
        // Enable event modal
        element.attr("data-toggle", "modal");
        element.attr("data-target", "#event-modal");
      } else {
        // Blur past events
        element.css("opacity", "0.5");
        element.css("cursor", "default");
      }
    },
    eventClick: function(event) {
      // Populate event modal
      eventModalSubject.text(event.modalSubjectCode);
      eventModalSubject.attr("data-original-title", event.modalSubjectName);
      eventModalClass.text(event.modalClass);
      eventModalDate.text(event.modalDate);
      eventModalTime.text(event.modalTime);
      eventModalVenue.text(event.modalVenue);
    }
  });

  // Cancel class
  eventModalCancelButton.click(function() {
    alert("Cancel!");
  });

  // Replace class
  eventModalReplaceButton.click(function() {
    alert("Replace!");
  });

}
