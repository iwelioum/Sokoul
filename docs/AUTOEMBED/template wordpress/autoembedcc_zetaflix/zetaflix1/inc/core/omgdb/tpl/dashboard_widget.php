<div class="navigation">
    <ul>
        <li id="zetadashcont-reported-click" class="zetaflix-dashaboard-navigation" data-id="reported"><?php _z('Reports'); ?> <span id="response-count-report-unread-span">(0)</span></li>
        <li id="zetadashcont-inbox-click" class="zetaflix-dashaboard-navigation" data-id="inbox"><?php _z('Contact'); ?> <span id="response-count-contact-unread-span">(0)</span></li>
        <li class="zetaflix-dashaboard-navigation" data-id="more"><?php _z('More'); ?></li>
    </ul>
</div>
<div class="box-content">
    <input id="omegadb-inboxes-nonce" type="hidden" value="<?php echo wp_create_nonce('omegadb-inboxes-nonce'); ?>">

    <div id="zetadashcont-reported" class="dashcont">
        <header>
            <span class="title"><?php _z('Reported content'); ?></span>
            <div class="right">
                <span id="response-count-report-unread" class="count unread">0</span>
                <span id="response-count-report-total" class="count total">0</span>
            </div>
        </header>
        <div id="response-inboxes-report" class="items">
            <div class="onload"></div>
            <div class="hidden">{{REPORTS_TEMPLATE}}</div>
        </div>
        <div id="inboxes-paginator-report" class="paginator hidden">
            <button id="inboxes-btn-loadmore-report" href="#" class="button button-primary button-small inboxes-loadmore" data-type="report"><?php _z('Load more'); ?></button>
            <input id="inboxes-input-report" type="hidden" value="">
        </div>
    </div>

    <div id="zetadashcont-inbox" class="dashcont">
        <header>
            <span class="title"><?php _z('Contact messages'); ?></span>
            <div class="right">
                <span id="response-count-contact-unread" class="count unread">0</span>
                <span id="response-count-contact-total" class="count total">0</span>
            </div>
        </header>
        <div id="response-inboxes-contact" class="items">
            <div class="onload"></div>
            <div class="hidden">{{CONTACT_TEMPLATE}}</div>
        </div>
        <div id="inboxes-paginator-contact" class="paginator hidden">
            <button id="inboxes-btn-loadmore-contact" href="#" class="button button-primary button-small inboxes-loadmore" data-type="contact"><?php _z('Load more'); ?></button>
            <input id="inboxes-input-contact" type="hidden" value="">
        </div>
    </div>

    <div id="zetadashcont-more" class="dashcont">
        <header>
            <span class="title"><?php _z('Delete all messages'); ?></span>
            <p>
                <a href="<?php echo admin_url("admin-ajax.php?action=omegadb_inboxes_cleaner&type=zetaflix_report&nonce=".$nonce); ?>" class="button button-small" onclick="return confirm('<?php _z('Do you really want to continue?'); ?>')"><?php _z('Reports'); ?></a>
                - <?php _z('or'); ?> -
                <a href="<?php echo admin_url("admin-ajax.php?action=omegadb_inboxes_cleaner&type=zetaflix_contact&nonce=".$nonce); ?>" class="button button-small" onclick="return confirm('<?php _z('Do you really want to continue?'); ?>')"><?php _z('Contact'); ?></a>
            </p>
        </header>
        <header>
            <span class="title"><?php _z('Quick access'); ?></span>
        </header>
        <ul class="listing">
            <li><a href="<?php echo admin_url("admin.php?page=omgdb"); ?>"><?php _z('Omegadb importer'); ?></a></li>
            <li><a href="<?php echo admin_url("admin.php?page=omgdb-settings"); ?>"><?php _z('Omegadb settings'); ?></a></li>
            <li><a href="<?php echo admin_url("themes.php?page=zetaflix"); ?>"><?php _z('Theme options'); ?></a></li>
            <li><a href="<?php echo admin_url("themes.php?page=zetaflix-license"); ?>"><?php _z('Theme license'); ?></a></li>
            <li><a href="<?php echo admin_url("tools.php?page=zetaflix-database"); ?>"><?php _z('Database tool'); ?></a></li>
            <li><a href="<?php echo admin_url("options-permalink.php#zetaflix-permalinks"); ?>"><?php _z('Permalinks'); ?></a></li>
            <li><a href="<?php echo admin_url("themes.php?page=zetaflix-ad"); ?>"><?php _z('Ad code manager'); ?></a></li>
        </ul>
        <header>
            <span class="title"><?php _z('Support'); ?></span>
        </header>
        <ul class="listing">
            <li><a href="https://bit.ly/zetathemes-forums" target="_blank"><?php _z('Support Forums'); ?></a></li>
            <li><a href="https://bit.ly/zetaflix-docs" target="_blank"><?php _z('Extended documentation'); ?></a></li>
            <li><a href="https://bit.ly/zetaflix-changelog" target="_blank"><?php _z('Changelog'); ?></a></li>
        </ul>
    </div>
</div>
