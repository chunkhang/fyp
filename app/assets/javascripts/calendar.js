/* globals $ */

export function calendar() {

  // Initialize calendar
  var calendar = $("#calendar");
  initializeCalender();

  function initializeCalender() {
    calendar.fullCalendar({
      schedulerLicenseKey: "CC-Attribution-NonCommercial-NoDerivatives",
      eventSources: [
        {
          url: "/calendar/events",
          type: "GET",
          error: function() {
            console.log("Failed to fetch calendar events");
          }
        }
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
      // scrollTime: "08:00:00",
      minTime: "08:00:00",
      maxTime: "18:00:00",
      businessHours: {
        dow: [1, 2, 3, 4, 5, 6],
        start: "08:00",
        end: "18:00"
      },
      viewRender: handleViewRender,
      dayClick: handleDayClick
    });
  }

  function handleViewRender(view, element) {
    if (view.name != "timelineDay") {
      calendar.fullCalendar("today");
    }
  }

  function handleDayClick(date, event, view, resource) {
    if (view.name == "month") {
      calendar.fullCalendar("gotoDate", date.format());
      calendar.fullCalendar("changeView", "timelineDay");
    }
  }

}