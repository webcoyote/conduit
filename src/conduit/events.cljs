(ns conduit.events
  (:require
   [conduit.db :refer [default-db set-user-ls remove-user-ls]]
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx inject-cofx trim-v after path debug]]
   [conduit.router :as router]
   [day8.re-frame.http-fx] ;; even if we don't use this require its existence will cause the :http-xhrio effect handler to self-register with re-frame
   [day8.re-frame.tracing :refer-macros [fn-traced]]
   [ajax.core :refer [json-request-format json-response-format]]
   [clojure.string :as str]
   [cljs-time.coerce :refer [to-long]]))

;; -- Interceptors --------------------------------------------------------------
;; Every event handler can be "wrapped" in a chain of interceptors. Each of these
;; interceptors can do things "before" and/or "after" the event handler is executed.
;; They are like the "middleware" of web servers, wrapping around the "handler".
;; Interceptors are a useful way of factoring out commonality (across event
;; handlers) and looking after cross-cutting concerns like logging or validation.
;;
;; They are also used to "inject" values into the `coeffects` parameter of
;; an event handler, when that handler needs access to certain resources.
;;
;; Each event handler can have its own chain of interceptors. Below we create
;; the interceptor chain shared by all event handlers which manipulate user.
;; A chain of interceptors is a vector.
;; Explanation of `trim-v` is given further below.
;;
(def set-user-interceptor [(path :user)        ;; `:user` path within `db`, rather than the full `db`.
                           (after set-user-ls) ;; write user to localstore (after)
                           trim-v])            ;; removes first (event id) element from the event vec

;; After logging out clean up local-storage so that when a users refreshes
;; the browser she/he is not automatically logged-in, and because it's a
;; good practice to clean-up after yourself.
;;
(def remove-user-interceptor [(after remove-user-ls)])

;; -- Helpers -----------------------------------------------------------------
;;
(def api-url "https://conduit.productionready.io/api")

(defn endpoint [& params]
  "Concat any params to api-url separated by /"
  (str/join "/" (concat [api-url] params)))

(defn auth-header [db]
  "Get user token and format for API authorization"
  (let [token (get-in db [:user :token])]
    (if token
      [:Authorization (str "Token " token)]
      nil)))

(reg-fx
 :set-url
 (fn [{:keys [url]}]
   (router/set-token! url)))

;; -- Event Handlers ----------------------------------------------------------
;;
(reg-event-fx   ;; usage: (dispatch [:initialise-db])
 :initialise-db ;; sets up initial application state

 ;; the interceptor chain (a vector of interceptors)
 [(inject-cofx :local-store-user)] ;; gets user from localstore, and puts into coeffects arg

 ;; the event handler (function) being registered
 (fn-traced [{:keys [local-store-user]} _]                    ;; take 2 vals from coeffects. Ignore event vector itself.
            {:db (assoc default-db :user local-store-user)})) ;; what it returns becomes the new application state

(reg-event-fx                                                                ;; usage: (dispatch [:set-active-page {:page :home})
 :set-active-page                                                            ;; triggered when the user clicks on a link that redirects to a another page
 (fn-traced [{:keys [db]} [_ {:keys [page]}]]
  {:db (assoc db :active-page page)}))

;; -- GET Profile @ /api/profiles/:username -----------------------------------
;;
(reg-event-fx                          ;; usage (dispatch [:get-user-profile {:profile "profile"}])
 :get-user-profile                     ;; triggered when the profile page is loaded
 (fn-traced [{:keys [db]} [_ params]]  ;; params = {:profile "profile"}
            {:db         (assoc-in db [:loading :profile] true)
             :http-xhrio {:method          :get
                          :uri             (endpoint "profiles" (:profile params))    ;; evaluates to "api/profiles/:profile"
                          :headers         (auth-header db)                           ;; get and pass user token obtained during login
                          :response-format (json-response-format {:keywords? true})   ;; json response and all keys to keywords
                          :on-success      [:get-user-profile-success]                ;; trigger get-user-profile-success
                          :on-failure      [:api-request-error :get-user-profile]}})) ;; trigger api-request-error with :get-user-profile

(reg-event-db
 :get-user-profile-success
 (fn-traced [db [_ {profile :profile}]]
            (-> db
                (assoc-in [:loading :profile] false)
                (assoc :profile profile))))

;; -- POST Login @ /api/users/login -------------------------------------------
;;
(reg-event-fx                              ;; usage (dispatch [:login user])
 :login                                    ;; triggered when a users submits login form
 (fn-traced [{:keys [db]} [_ credentials]] ;; credentials = {:email ... :password ...}
            {:db         (assoc-in db [:loading :login] true)
             :http-xhrio {:method          :post
                          :uri             (endpoint "users" "login")               ;; evaluates to "api/users/login"
                          :params          {:user credentials}                      ;; {:user {:email ... :password ...}}
                          :format          (json-request-format)                    ;; make sure it's json
                          :response-format (json-response-format {:keywords? true}) ;; json response and all keys to keywords
                          :on-success      [:login-success]                         ;; trigger login-success
                          :on-failure      [:api-request-error :login]}}))          ;; trigger api-request-error with :login

(reg-event-fx
 :login-success
 ;; The standard set of interceptors, defined above, which we
 ;; use for all user-modifying event handlers. Looks after
 ;; writing user to localStorage.
 ;; NOTE: this chain includes `path` and `trim-v`
 set-user-interceptor

 ;; The event handler function.
 ;; The "path" interceptor in `set-user-interceptor` means 1st parameter is the
 ;; value at `:user` path within `db`, rather than the full `db`.
 ;; And, further, it means the event handler returns just the value to be
 ;; put into `:user` path, and not the entire `db`.
 ;; So, a path interceptor makes the event handler act more like clojure's `update-in`
 (fn-traced [{user :db} [{props :user}]]
            {:db (merge user props)
             :dispatch-n (list [:complete-request :login]
                               [:set-active-page {:page :home}])}))

;; -- POST Registration @ /api/users ------------------------------------------
;;
(reg-event-fx                               ;; usage (dispatch [:register-user registration])
 :register-user                             ;; triggered when a users submits registration form
 (fn-traced [{:keys [db]} [_ registration]] ;; registration = {:username ... :email ... :password ...}
            {:db         (assoc-in db [:loading :register-user] true)
             :http-xhrio {:method          :post
                          :uri             (endpoint "users")                       ;; evaluates to "api/users"
                          :params          {:user registration}                     ;; {:user {:username ... :email ... :password ...}}
                          :format          (json-request-format)                    ;; make sure it's json
                          :response-format (json-response-format {:keywords? true}) ;; json response and all keys to keywords
                          :on-success      [:register-user-success]                 ;; trigger login-success
                          :on-failure      [:api-request-error :register-user]}}))  ;; trigger api-request-error with :login-success

(reg-event-fx
 :register-user-success
 ;; The standard set of interceptors, defined above, which we
 ;; use for all user-modifying event handlers. Looks after
 ;; writing user to LocalStore.
 ;; NOTE: this chain includes `path` and `trim-v`
 set-user-interceptor

 ;; The event handler function.
 ;; The "path" interceptor in `set-user-interceptor` means 1st parameter is the
 ;; value at `:user` path within `db`, rather than the full `db`.
 ;; And, further, it means the event handler returns just the value to be
 ;; put into `:user` path, and not the entire `db`.
 ;; So, a path interceptor makes the event handler act more like clojure's `update-in`
 (fn-traced [{user :db} [{props :user}]]
            {:db (merge user props)
             :dispatch-n (list [:complete-request :register-user]
                               [:set-active-page {:page :home}])}))

;; -- PUT Update User @ /api/user ---------------------------------------------
;;
(reg-event-fx                       ;; usage (dispatch [:update-user user])
 :update-user                       ;; triggered when a users updates settgins
 (fn-traced [{:keys [db]} [_ user]] ;; user = {:img ... :username ... :bio ... :email ... :password ...}
            {:db         (assoc-in db [:loading :update-user] true)
             :http-xhrio {:method          :put
                          :uri             (endpoint "user")                        ;; evaluates to "api/user"
                          :params          {:user user}                             ;; {:user {:img ... :username ... :bio ... :email ... :password ...}}
                          :headers         (auth-header db)                         ;; get and pass user token obtained during login
                          :format          (json-request-format)                    ;; make sure our request is json
                          :response-format (json-response-format {:keywords? true}) ;; json response and all keys to keywords
                          :on-success      [:update-user-success]                   ;; trigger update-user-success
                          :on-failure      [:api-request-error :update-user]}}))    ;; trigger api-request-error with :update-user

(reg-event-fx
 :update-user-success
 ;; The standard set of interceptors, defined above, which we
 ;; use for all user-modifying event handlers. Looks after
 ;; writing user to LocalStore.
 ;; NOTE: this chain includes `path` and `trim-v`
 set-user-interceptor

 ;; The event handler function.
 ;; The "path" interceptor in `set-user-interceptor` means 1st parameter is the
 ;; value at `:user` path within `db`, rather than the full `db`.
 ;; And, further, it means the event handler returns just the value to be
 ;; put into `:user` path, and not the entire `db`.
 ;; So, a path interceptor makes the event handler act more like clojure's `update-in`
 (fn-traced [{user :db} [{props :user}]]
            {:db (merge user props)
             :dispatch [:complete-request :update-user]}))

;; -- Logout ------------------------------------------------------------------
;;
(reg-event-fx ;; usage (dispatch [:logout])
 :logout
 ;; This interceptor, defined above, makes sure
 ;; that we clean up localStorage after logging-out
 ;; the user.
 remove-user-interceptor
 ;; The event handler function removes the user from
 ;; app-state = :db and sets the url to "/".
 (fn-traced [{:keys [db]} _]
            {:db      (dissoc db :user) ;; remove user from db
             :dispatch [:set-active-page {:page :home}]}))

;; -- Request Handlers -----------------------------------------------------------
;;
(reg-event-db
 :complete-request                ;; when we complete a request we need to clean up
 (fn-traced [db [_ request-type]] ;; few things so that our ui is nice and tidy
            (assoc-in db [:loading request-type] false)))

(reg-event-fx
 :api-request-error                                                                         ;; triggered when we get request-error from the server
 (fn-traced [{:keys [db]} [_ request-type response]]                                        ;; destructure to obtain request-type and response
            {:db (assoc-in db [:errors request-type] (get-in response [:response :errors])) ;; save in db so that we can display it to the user
             :dispatch [:complete-request request-type]}))
