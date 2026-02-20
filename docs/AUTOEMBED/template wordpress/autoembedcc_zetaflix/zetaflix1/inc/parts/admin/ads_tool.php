<div class="wrap zetaads">
    <h1><?php _z('Ad code manager'); ?></h1>
    <div id="ad-manage-codes" class="updated hidden">
        <p><strong><?php _e('Settings saved.'); ?></strong></p>
    </div>
    <nav class="menu">
        <ul id="zetaadmenu" class="items">
            <li data-tab="integrations" class="nav-tab nav-tab-active"><?php _z('Code integrations'); ?></li>
            <li data-tab="homepage" class="nav-tab"><?php _z('Homepage'); ?></li>
            <li data-tab="singlepost" class="nav-tab"><?php _z('Single Post'); ?></li>
			<li data-tab="archives" class="nav-tab"><?php _z('Archives'); ?></li>
            <li data-tab="videoplayer" class="nav-tab"><?php _z('Video Player'); ?></li>
            <li data-tab="linksmodule" class="nav-tab"><?php _z('Links redirection'); ?></li>
        </ul>
    </nav>

    <form id="zetaadmanage">
        <div id="zetaad-integrations" class="tab-content on">
            <h3><?php _z('Header code integration'); ?></h3>
            <?php $this->textarea('_zetaflix_header_code', $headcode); ?>
            <p><?php _z('Enter the code which you need to place before closing tag. (ex: Google Webmaster Tools verification, Bing Webmaster Center, BuySellAds Script, Alexa verification etc.)'); ?></p>
            <hr>
            <h3><?php _z('Footer code integration'); ?></h3>
            <?php $this->textarea('_zetaflix_footer_code', $footcode); ?>
            <p><?php _z('Enter the codes which you need to place in your footer. (ex: Google Analytics, Clicky, STATCOUNTER, Woopra, Histats, etc.)'); ?></p>
        </div>

        <div id="zetaad-homepage" class="tab-content">
            <h3><?php _z('Homepage > Ad Block #1 [Desktop]'); ?></h3>
            <?php $this->textarea('_zetaflix_adhome', $adhomedk, __z('Use HTML code')); ?>
            <h3><?php _z('Homepage > Ad Block #1 [Mobile]'); ?></h3>
            <?php $this->textarea('_zetaflix_adhome_mobile', $adhomemb, __z('Use HTML code')); ?>
			<hr>
            <h3><?php _z('Homepage > Ad Block #2 [Desktop]'); ?></h3>
            <?php $this->textarea('_zetaflix_adhome2', $adhomedk2, __z('Use HTML code')); ?>
            <h3><?php _z('Homepage > Ad Block #2 [Mobile]'); ?></h3>
            <?php $this->textarea('_zetaflix_adhome2_mobile', $adhomemb2, __z('Use HTML code')); ?>
			<hr>
            <h3><?php _z('Homepage > Ad Block #3 [Desktop]'); ?></h3>
            <?php $this->textarea('_zetaflix_adhome3', $adhomedk3, __z('Use HTML code')); ?>
            <h3><?php _z('Homepage > Ad Block #3 [Mobile]'); ?></h3>
            <?php $this->textarea('_zetaflix_adhome3_mobile', $adhomemb3, __z('Use HTML code')); ?>
        </div>

        <div id="zetaad-singlepost" class="tab-content">
            <h3><?php _z('Single > Ad Block - Middle [Desktop]'); ?></h3>
            <?php $this->textarea('_zetaflix_adsingle', $adsingdk, __z('Use HTML code')); ?>
            <h3><?php _z('Single > Ad Block - Middle [Mobile]'); ?></h3>
            <?php $this->textarea('_zetaflix_adsingle_mobile', $adsingmb, __z('Use HTML code')); ?>
            <p><?php _z('This is an optional field'); ?></p>
        </div>
		
        <div id="zetaad-archives" class="tab-content">
            <h3><?php _z('Archive > Ad Block - Top [Desktop]'); ?></h3>
            <?php $this->textarea('_zetaflix_adarchive', $adarchdk, __z('Use HTML code')); ?>
            <h3><?php _z('Archive > Ad Block - Top [Mobile]'); ?></h3>
            <?php $this->textarea('_zetaflix_adarchive_mobile', $adarchmb, __z('Use HTML code')); ?>
            <p><?php _z('This is an optional field'); ?></p>
            <h3><?php _z('Archive > Ad Block - Bottom [Desktop]'); ?></h3>
            <?php $this->textarea('_zetaflix_adarchive2', $adarchdk2, __z('Use HTML code')); ?>
            <h3><?php _z('Archive > Ad Block - Bottom [Mobile]'); ?></h3>
            <?php $this->textarea('_zetaflix_adarchive2_mobile', $adarchmb2, __z('Use HTML code')); ?>
            <p><?php _z('This is an optional field'); ?></p>
        </div>

        <div id="zetaad-videoplayer" class="tab-content">
            <h3><?php _z('Video Player > Ad Block #1 [Desktop]'); ?></h3>
            <?php $this->textarea('_zetaflix_adplayer', $adplaydk, __z('Use HTML code')); ?>
            <h3><?php _z('Video Player > Ad Block #1 [Mobile]'); ?></h3>
            <?php $this->textarea('_zetaflix_adplayer_mobile', $adplaymb, __z('Use HTML code')); ?>
            <p><?php _z('This is an optional field'); ?></p>
        </div>
        <div id="zetaad-linksmodule" class="tab-content">
            <h3><?php _z('Links > Ad Block - Top [Desktop]'); ?></h3>
            <?php $this->textarea('_zetaflix_adlinktop', $adlinktd, __z('Use HTML code')); ?>
            <h3><?php _z('Links > Ad Block - Top [Mobile]'); ?></h3>
            <?php $this->textarea('_zetaflix_adlinktop_mobile', $adlinktm, __z('Use HTML code')); ?>
            <p><?php _z('This is an optional field'); ?></p>
            <hr>
            <h3><?php _z('Links > Ad Block - Bottom [Desktop]'); ?></h3>
            <?php $this->textarea('_zetaflix_adlinkbottom', $adlinkbd, __z('Use HTML code')); ?>
            <h3><?php _z('Links > Ad Block - Bottom [Mobile]'); ?></h3>
            <?php $this->textarea('_zetaflix_adlinkbottom_mobile', $adlinkbm, __z('Use HTML code')); ?>
            <p><?php _z('This is an optional field'); ?></p>
        </div>
        <hr>
        <div class="control">
            <input id="zetaadsavebutton" type="submit" class="button button-primary" data-text="<?php _z('Save changes'); ?>" value="<?php _z('Save changes'); ?>">
            <input type="hidden" name="nonce" value="<?php echo $nonce; ?>">
            <input type="hidden" name="action" value="zetaadmanage">
        </div>
    </form>

</div>
