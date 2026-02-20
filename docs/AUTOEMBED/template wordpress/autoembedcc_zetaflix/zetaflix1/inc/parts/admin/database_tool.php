<div class="wrap zetaflixdb">
    <h1><?php _z('Database tool for ZetaFlix'); ?></h1>
    <h2><?php _z('Caution, any process that runs from this tool is irreversible'); ?></h2>
    <p><?php _z('Before proceeding with any action that you wish to execute from this tool, we recommend making a backup of your database, all elimination processes are irreversible.'); ?></p>


    <div class="metabox-holder">

        <?php if(!$newlink){ ?>
        <div id="zetalinkmod" class="postbox required">
            <h3><span><?php _z('Update Links Module'); ?></span></h3>
            <div class="inside">
                <p><?php _z('This new version require that you update the content for the links module, the process is safe'); ?></p>
                <form id="zetalinkmod_form">
                    <input id="zetalinkmod_submit" type="submit" class="button button-primary" value="<?php _z('Update module'); ?>">
                    <input id="zetalinkmod_step" name="step" type="hidden" value="1">
                    <input id="zetalinkmod_run" name="run" type="hidden" value="assume">
                    <input id="zetalinkmod_action" name="action" type="hidden" value="zetaupdate_linksdb">
                </form>
            </div>
            <div class="response">
                <span class="spinner is-active"></span>
                <div class="zeta-progress">
                    <div style="width:0%;"></div>
                </div>
            </div>
        </div>
        <?php } ?>


        <div id="zetabox_license" class="postbox">
            <h3><span><?php _z('Reset License'); ?></span></h3>
            <div class="inside">
                <p><?php _z('Delete all the data that your license has registered in your database.'); ?></p>
                <hr>
                <p>
                    <a href="#" data-run='license' class="button button-primary zetadatabasetool" data-msg="<?php _z('Running this process will erase all zetaflix data. It\'s irreversible, do you want to continue?');?>"><?php _z('Run process'); ?></a>
                    <span id="zetadatabasetool_license" class="agotime"><?php echo isset($timerun['license']) ? human_time_diff($timerun['license'], current_time('timestamp')) : $never; ?></span>
                </p>
            </div>
        </div>
        <div id="zetabox_transients" class="postbox">
            <h3><span><?php _z('Clear Transient Options'); ?></span></h3>
            <div class="inside">
                <p><?php _z('Removing transient options can help solve some system problems.'); ?></p>
                <hr>
                <p>
                    <a href="#" data-run='transients' class="button button-primary zetadatabasetool" data-msg="<?php _z('Running this process will transient options. It\'s irreversible, do you want to continue?');?>"><?php _z('Run process'); ?></a>
                    <span id="zetadatabasetool_transients" class="agotime"><?php echo isset($timerun['transients']) ? human_time_diff($timerun['transients'], current_time('timestamp')) : $never; ?></span>
                </p>
            </div>
        </div>
        <div id="zetabox_userfavorites" class="postbox">
            <h3><span><?php _z('Reset User List'); ?></span></h3>
            <div class="inside">
                <p><?php _z('Reset the list of all your users.'); ?></p>
                <hr>
                <p>
                    <a href="#" data-run='userfavorites' class="button button-primary zetadatabasetool" data-msg="<?php _z('Running this process will erase all user list data. It\'s irreversible, do you want to continue?');?>"><?php _z('Run process'); ?></a>
                    <span id="zetadatabasetool_userfavorites" class="agotime"><?php echo isset($timerun['userfavorites']) ? human_time_diff($timerun['userfavorites'], current_time('timestamp')) : $never; ?></span>
                </p>
            </div>
        </div>
        <div id="zetabox_userviews" class="postbox">
            <h3><span><?php _z('Reset User Seen'); ?></span></h3>
            <div class="inside">
                <p><?php _z('Restore the list of seen of all your users.'); ?></p>
                <hr>
                <p>
                    <a href="#" data-run='userviews' class="button button-primary zetadatabasetool" data-msg="<?php _z('Running this process will erase all user seen lists data. It\'s irreversible, do you want to continue?');?>"><?php _z('Run process'); ?></a>
                    <span id="zetadatabasetool_userviews" class="agotime"><?php echo isset($timerun['userviews']) ? human_time_diff($timerun['userviews'], current_time('timestamp')) : $never; ?></span>
                </p>
            </div>
        </div>
        <div id="zetabox_reports" class="postbox">
            <h3><span><?php _z('Reset User Reports'); ?></span></h3>
            <div class="inside">
                <p><?php _z('Remove all user reports'); ?></p>
                <hr>
                <p>
                    <a href="#" data-run='reports' class="button button-primary zetadatabasetool" data-msg="<?php _z('Running this process will erase all user reports data. It\'s irreversible, do you want to continue?');?>"><?php _z('Run process'); ?></a>
                    <span id="zetadatabasetool_reports" class="agotime"><?php echo isset($timerun['reports']) ? human_time_diff($timerun['reports'], current_time('timestamp')) : $never; ?></span>
                </p>
            </div>
        </div>
        <div id="zetabox_ratings" class="postbox">
            <h3><span><?php _z('Reset User Ratings'); ?></span></h3>
            <div class="inside">
                <p><?php _z('Reset rating counter on all content.'); ?></p>
                <hr>
                <p>
                    <a href="#" data-run='ratings' class="button button-primary zetadatabasetool" data-msg="<?php _z('Running this process will erase all user ratings data. It\'s irreversible, do you want to continue?');?>"><?php _z('Run process'); ?></a>
                    <span id="zetadatabasetool_ratings" class="agotime"><?php echo isset($timerun['ratings']) ? human_time_diff($timerun['ratings'], current_time('timestamp')) : $never; ?></span>
                </p>
            </div>
        </div>
        <div id="zetabox_mainslider" class="postbox">
            <h3><span><?php _z('Reset Main Slider'); ?></span></h3>
            <div class="inside">
                <p><?php _z('Reset all the content that was added to the main slider start a new list.'); ?></p>
                <p>
                    <a href="#" data-run='mainslider' class="button button-primary zetadatabasetool" data-msg="<?php _z('Running this process will remove all contents added to the main slider. It\'s irreversible, do you want to continue?');?>"><?php _z('Run process'); ?></a>
                    <span id="zetadatabasetool_mainslider" class="agotime"><?php echo isset($timerun['mainslider']) ? human_time_diff($timerun['mainslider'], current_time('timestamp')) : $never; ?></span>
                </p>
            </div>
        </div>
        <div id="zetabox_postviews" class="postbox">
            <h3><span><?php _z('Reset Post Views'); ?></span></h3>
            <div class="inside">
                <p><?php _z('Reset views counter on all content.'); ?></p>
                <hr>
                <p>
                    <a href="#" data-run='postviews' class="button button-primary zetadatabasetool" data-msg="<?php _z('Running this process will erase all post views data. It\'s irreversible, do you want to continue?');?>"><?php _z('Run process'); ?></a>
                    <span id="zetadatabasetool_postviews" class="agotime"><?php echo isset($timerun['postviews']) ? human_time_diff($timerun['postviews'], current_time('timestamp')) : $never; ?></span>
                </p>
            </div>
        </div>
        <div id="zetabox_genpages" class="postbox">
            <h3><span><?php _z('Generate Pages'); ?></span></h3>
            <div class="inside">
                <p><?php _z('Generate all the required pages.'); ?></p>
                <hr>
                <p>
                    <a href="#" data-run='genpages' class="button button-primary zetadatabasetool" data-msg="<?php _z('Running this process will might create duplicate pages if it already exist, do you want to continue?');?>"><?php _z('Run process'); ?></a>
                    <span id="zetadatabasetool_genpages" class="agotime"><?php echo isset($timerun['genpages']) ? human_time_diff($timerun['genpages'], current_time('timestamp')) : $never; ?></span>
                </p>
            </div>
        </div>
        <div id="zetabox_genwidgets" class="postbox">
            <h3><span><?php _z('Generate Widgets'); ?></span></h3>
            <div class="inside">
                <p><?php _z('Generate and set default widgets for all sidebars.'); ?></p>
                <hr>
                <p>
                    <a href="#" data-run='genwidgets' class="button button-primary zetadatabasetool" data-msg="<?php _z('Running this process will overwrite any existing widgets you may have on your widgets. It\'s irreversible, do you want to continue?');?>"><?php _z('Run process'); ?></a>
                    <span id="zetadatabasetool_genwidgets" class="agotime"><?php echo isset($timerun['genwidgets']) ? human_time_diff($timerun['genwidgets'], current_time('timestamp')) : $never; ?></span>
                </p>
            </div>
        </div>
    </div>
    <input type="hidden" id="zetalinkmod_nonce" value="<?php echo $nonce; ?>">
</div>
