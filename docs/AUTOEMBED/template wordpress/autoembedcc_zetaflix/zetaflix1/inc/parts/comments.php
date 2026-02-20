<?php
/*
* -------------------------------------------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @aopyright: (c) 2023 Zetathemes. All rights reserved
* -------------------------------------------------------------------------------------
*
* @since 1.0.0
*
*/

switch(zeta_get_option('comments')){

    case 'wp':
        comments_template('', true);
        break;

    case 'fb':

        $fbcolor = zeta_get_option('fbscheme','light');
        $fbnumps = zeta_get_option('fbnumber','10');
        $postppl = get_the_permalink();
        $out  = "<div id='comments' class='extcom'>";
        $out .= "<div style='width:100%' class='fb-comments' data-href='{$postppl}' data-order-by='social' data-numposts='{$fbnumps}' data-colorscheme='{$fbcolor}' data-width='100%'></div>";
        $out .= "</div>";
        echo $out;
        break;

    case 'dq':
        $sname = zeta_get_option('dqshortname');
        $dlink = "<a href='https://help.disqus.com/installation/whats-a-shortname' target='_blank'>".__z('more info')."</a>";

        $out = "<div id='comments' class='extcom'>";
        if($sname){
            $out .= "<div id='disqus_thread'></div>";
        } else{
            $out .= "<p>".sprintf( __z('<strong>Disqus:</strong> add shortname your comunity %s'), $dlink)."</p>";
        }
        $out .= "</div>";

        echo $out
        ;

        break;
}
